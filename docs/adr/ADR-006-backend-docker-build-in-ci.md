# ADR-006: Backend Docker Images Built in CI — Not on VPS

Date: 2026-03-05
Status: ACCEPTED

---

## Context

The ZyntaPOS backend consists of three Ktor services (`api`, `license`, `sync`), each with a
multi-stage Dockerfile. Stage 1 runs `./gradlew shadowJar` to produce a fat JAR; Stage 2 copies
the JAR into a minimal JRE 21 Alpine runtime image.

The original deploy workflow (`cd-deploy.yml`) used `docker compose up -d --build`, which
instructed the Contabo VPS to build all three Docker images from source on every deployment.

This caused two cascading failures:

1. **Missing `gradlew`** — The Dockerfiles do `COPY gradlew ./` and `COPY gradle ./gradle`, but
   the Gradle wrapper files were either absent (`backend/api/`) or listed in `.gitignore`
   (`backend/*/gradle/`). Docker failed with:
   ```
   failed to solve: failed to read dockerfile: open /build/gradlew: no such file or directory
   ```

2. **OWASP build script compilation error** — Even after the wrapper was added, the Kotlin build
   script failed because `nvd.apiDelay` (v9.x syntax) does not exist in OWASP Dependency Check
   plugin 10.0.4 (which uses `nvd.delay`):
   ```
   e: build.gradle.kts:30:9: Unresolved reference: apiDelay
   ```

Beyond these bugs, building on the VPS is architecturally wrong:
- It downloads all Gradle and Maven dependencies on every deploy (~600 MB per service)
- It couples the production server to a JDK, Gradle, and build environment
- Build failures block the entire deployment pipeline
- There is no image reproducibility — the same commit could produce different images across
  runs depending on dependency resolution

---

## Decision

**Build Docker images in CI (GitHub Actions) and push to the GitHub Container Registry (GHCR).
The VPS only pulls pre-built images — it never compiles code or runs Gradle.**

### CI side (`ci-gate.yml` — `build-backend-images` job)

```yaml
build-backend-images:
  needs: build-and-test
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  strategy:
    matrix:
      service: [api, license, sync]
  permissions:
    contents: read
    packages: write        # GITHUB_TOKEN gets GHCR push rights automatically
  steps:
    - uses: docker/login-action@v3
      with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
    - uses: docker/build-push-action@v6
      with:
        context: backend/${{ matrix.service }}
        push: true
        tags: |
          ghcr.io/${{ github.repository_owner }}/zyntapos-${{ matrix.service }}:latest
          ghcr.io/${{ github.repository_owner }}/zyntapos-${{ matrix.service }}:${{ github.sha }}
```

The `trigger-deploy` job now depends on both `build-and-test` AND `build-backend-images` —
a deploy cannot start until verified images are in GHCR.

### `docker-compose.yml` (VPS side)

```yaml
# Before
api:
  build:
    context: ./backend/api
    dockerfile: Dockerfile

# After
api:
  image: ghcr.io/sendtodilanka/zyntapos-api:latest
```

### `cd-deploy.yml` (VPS deploy step)

```bash
# Added: authenticate with GHCR before pulling
echo "${{ secrets.PAT_TOKEN }}" | docker login ghcr.io -u sendtodilanka --password-stdin

docker compose pull --quiet
docker compose up -d --remove-orphans   # no --build
```

`PAT_TOKEN` must have the `read:packages` scope to pull from GHCR (in addition to the
existing `repo` scope). `GITHUB_TOKEN` is used for the push (in CI); the PAT is used for
the pull (on VPS, which is outside the GitHub Actions context).

### Gradle wrapper in backend service directories

The Dockerfiles still run `./gradlew shadowJar` inside the Docker build (Stage 1). For CI
to build the images, the wrapper must be in the repository. Fixed in `.gitignore`:

```diff
- backend/*/gradle/
+ backend/*/.gradle/
  !backend/*/gradle/wrapper/gradle-wrapper.jar
```

`gradlew` and `gradle/wrapper/gradle-wrapper.{properties,jar}` were copied from the root
project (Gradle 8.14.3) to `backend/api/`, `backend/license/`, and `backend/sync/`.

### OWASP nvd.apiDelay → nvd.delay rename

OWASP Dependency Check 10.0.4 renamed the NVD API rate-limit property:

```kotlin
// Before (v9.x — does not compile with 10.x):
nvd { apiDelay = 3500 }

// After (v10.x):
nvd { delay = 3500 }
```

Fixed in `backend/{api,license,sync}/build.gradle.kts`.

---

## Consequences

### Positive

- **VPS is stateless** — no JDK, Gradle, or build cache required on the production server.
  The VPS only needs Docker.

- **Immutable image tags** — every push to `main` produces both a `:latest` tag and an
  immutable `:<sha>` tag. Rollback is precise: update the image tag in `docker-compose.yml`
  to a previous SHA and redeploy.

- **Build failures are caught in CI** — a broken Gradle build blocks the deploy trigger;
  broken images never reach the VPS.

- **Faster deploys** — VPS deployment is now `docker compose pull` + `docker compose up`,
  which takes ~30s instead of the 5-10 minutes a full Gradle + Docker build would take.

- **Consistent images** — the same SHA always produces the same image, regardless of when
  the VPS is provisioned or reprovisioned.

### Negative / Tradeoffs

- **PAT_TOKEN scope** — The PAT used for VPS access now needs `read:packages` in addition
  to `repo`. If regenerating the PAT, this scope must be added. Documented in
  `docs/todo/007-infrastructure-and-deployment.md` validation checklist.

- **First GHCR push is slow** — Each Docker build runs `./gradlew shadowJar` inside the
  Docker layer cache. Cold builds take 3–5 minutes per service (3 services in parallel).
  Subsequent builds are faster if the Gradle dependency layer is cached (Docker layer cache
  is not retained across runners by default — consider `cache-from: type=gha` if build time
  becomes a concern).

- **GHCR images are private** — Because the repository is private, pushed packages are also
  private. The VPS must authenticate before pulling. This is handled by the deploy step.

---

## References

- Commit `05438ca` — deploy pipeline architecture fix
- Commit `d44ae10` — OWASP nvd.apiDelay rename fix
- GitHub Actions run `22704980409` — the failing run that triggered this ADR
- `docs/architecture/deployment.md` — full deployment pipeline documentation
- `docs/todo/007-infrastructure-and-deployment.md` — TODO-007 validation checklist
