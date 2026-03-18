# Stream 3: UI/UX Fixes — G-series, MS-*, INV-*

**Master Plan:** `todo/missing-features-implementation-plan.md` (Sections G16, G17, G18, G21)
**Size:** M total (batch of S-size items, 1 session)
**Conflict Risk:** LOW — touches KMM feature module UI files only
**Dependencies:** None — all items are independent

> **NOTE:** This stream maps to the plan's "Stream 4" (G1-G21, INV-*, MS-*).
> The prompt numbering differs from the plan's stream numbering for practical grouping.
>
> **Deferred items (out of scope for this session):**
> - INV-1 (Barcode Scanner HAL) — requires platform `actual` implementations, too complex for UI batch
> - INV-2 (Variant Persistence) — needs use case + repository changes, not just UI
> - INV-5, INV-6, INV-7 (Supplier history, Bulk import, Batch select) — lower priority
> - INV-10 (TaxGroupScreen + UnitManagementScreen) — needs new screens from scratch, larger scope
> - G18 TaxGroup + UnitManagement routes — deferred because INV-10 screens don't exist yet

---

## Pre-Implementation (MANDATORY — do not skip)

1. Read `CLAUDE.md` fully (codebase context, module map, tech stack, conventions)
2. Read ALL files in `docs/adr/` (ADR-001 through ADR-008) — especially:
   - ADR-001 (BaseViewModel — all VMs MUST extend BaseViewModel)
   - ADR-002 (Domain model naming — no *Entity suffix)
3. Read `docs/architecture/` (module dependency diagrams)
4. Read `todo/missing-features-implementation-plan.md` FULLY — especially:
   - Section G16 (MS-1 to MS-6 — Multistore module gaps)
   - Section G17 (INV-1 to INV-10 — Inventory module gaps)
   - Section G18 (Navigation route gaps — 6 missing routes)
   - Section G21 (Phase 1.5 Quick Wins)
   - IMPLEMENTATION COMPLIANCE RULES section (MVI pattern, DRY rule, naming)
   - SESSION SCOPE GUIDANCE (G-series = S-M size each)
5. Run `echo $PAT` to confirm GitHub token is available
6. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Codebase Exploration (BEFORE writing any code)

```bash
# === Design System (REUSE these — do NOT create new components) ===
grep -r "^fun Zynta" composeApp/designsystem/src/commonMain/ --include="*.kt" | head -40

# === Existing navigation routes (understand the pattern) ===
cat composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/ZyntaRoute.kt

# === Existing NavGraph (where routes are registered) ===
find composeApp/navigation/ -name "ZyntaNavGraph.kt" -exec cat {} \;

# === Multistore module files ===
find composeApp/feature/multistore/ -name "*.kt" | sort
cat composeApp/feature/multistore/src/commonMain/kotlin/*/WarehouseViewModel.kt
cat composeApp/feature/multistore/src/commonMain/kotlin/*/NewStockTransferScreen.kt
cat composeApp/feature/multistore/src/commonMain/kotlin/*/StockTransferListScreen.kt
cat composeApp/feature/multistore/src/commonMain/kotlin/*/WarehouseRackDetailScreen.kt

# === Inventory module files ===
find composeApp/feature/inventory/ -name "*.kt" | sort
cat composeApp/feature/inventory/src/commonMain/kotlin/*/ProductDetailScreen.kt
cat composeApp/feature/inventory/src/commonMain/kotlin/*/ProductListScreen.kt
cat composeApp/feature/inventory/src/commonMain/kotlin/*/InventoryViewModel.kt

# === Dashboard module (for sparkline fix) ===
find composeApp/feature/dashboard/ -name "*.kt" | sort
cat composeApp/feature/dashboard/src/commonMain/kotlin/*/DashboardViewModel.kt

# === AsyncImage usage (follow existing pattern for INV-4) ===
grep -r "AsyncImage\|rememberAsyncImagePainter\|coil" composeApp/ --include="*.kt" -l

# === ExposedDropdownMenuBox usage (follow pattern for MS-1) ===
grep -r "ExposedDropdownMenuBox\|DropdownMenu" composeApp/ --include="*.kt" -l

# === Product search pattern (reuse for MS-1 product selector) ===
grep -r "ProductRepository\|productRepository\|search" composeApp/feature/inventory/ --include="*.kt" -l
```

---

## Items to Implement (4 batches, commit after each)

### Batch 1: Navigation Route Gaps (G18) — do FIRST (unblocks other fixes)

Add missing routes to `ZyntaRoute.kt` and register in `ZyntaNavGraph.kt`.

**Missing routes to add:**

| Route | Parameters | Referenced By |
|-------|-----------|---------------|
| `WarehouseRackList` | `warehouseId: String` | WarehouseDetailScreen |
| `WarehouseRackDetail` | `warehouseId: String, rackId: String?` | WarehouseRackListScreen |
| `CategoryDetail` | `categoryId: String?` | CategoryListScreen |
| `SupplierDetail` | `supplierId: String?` | SupplierListScreen |

**Steps:**
1. Read `ZyntaRoute.kt` — understand the `@Serializable` sealed class pattern
2. Add 4 new `data class` routes inside `ZyntaRoute` following EXACT existing pattern
3. Read `ZyntaNavGraph.kt` — understand `composable<Route>` registration pattern
4. Add NavHost entries for all 4 new routes
5. Wire navigation from parent screens (WarehouseDetailScreen → WarehouseRackList, etc.)

**Pattern to follow (from existing routes):**
```kotlin
@Serializable
data class WarehouseRackList(val warehouseId: String) : ZyntaRoute()

@Serializable
data class WarehouseRackDetail(
    val warehouseId: String,
    val rackId: String? = null
) : ZyntaRoute()
```

**Commit:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add composeApp/navigation/ composeApp/feature/ todo/missing-features-implementation-plan.md
git commit -m "feat(navigation): add missing routes for WarehouseRack, Category, Supplier [G18, INV-3, MS-3]

- WarehouseRackList(warehouseId) and WarehouseRackDetail(warehouseId, rackId) routes
- CategoryDetail(categoryId) and SupplierDetail(supplierId) routes
- NavHost entries and navigation wiring from parent screens

Plan file updated: G18, INV-3, MS-3 marked complete"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before Batch 2
```

---

### Batch 2: Multistore Module Fixes (G16) — after Batch 1 pipeline green

#### MS-1 (HIGH): Product selector in NewStockTransferScreen

- Replace manual product ID text field with search-as-you-type dropdown
- Use `ExposedDropdownMenuBox` — follow pattern from inventory module
- Wire to `WarehouseViewModel` — add new Intent: `SearchProducts(query: String)`
- Call existing `ProductRepository.search()` via use case
- Display: product name + SKU + current stock

**Steps:**
1. Read `NewStockTransferScreen.kt` — find the product ID text field
2. Read `InventoryViewModel` search pipeline as reference pattern
3. Add `SearchProducts(query: String)` intent to `WarehouseViewModel`
4. Add `productSearchResults: List<Product>` to `WarehouseState`
5. Replace TextField with `ExposedDropdownMenuBox` in `NewStockTransferScreen`
6. On product select, set `selectedProductId` in state

#### MS-2 (HIGH): Warehouse name display in StockTransferListScreen

- `StockTransferCard` shows raw UUIDs for sourceWarehouseId/destWarehouseId
- Resolve names from `WarehouseState.warehouses` list
- Create a local lookup: `val warehouseMap = warehouses.associateBy { it.id }`
- Display `warehouseMap[transfer.sourceWarehouseId]?.name ?: transfer.sourceWarehouseId`

#### MS-4 (MEDIUM): Back button on WarehouseRackDetailScreen

- Add `TopAppBar` with `navigationIcon` back arrow
- Follow pattern from other detail screens (e.g., `WarehouseDetailScreen`)

**Commit:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add composeApp/feature/multistore/ todo/missing-features-implementation-plan.md
git commit -m "fix(multistore): add product selector and warehouse name display [MS-1, MS-2, MS-4]

- MS-1: ExposedDropdownMenuBox product search in NewStockTransferScreen
- MS-2: Warehouse name resolution in StockTransferListScreen (no more raw UUIDs)
- MS-4: TopAppBar with back navigation in WarehouseRackDetailScreen

Plan file updated: MS-1, MS-2, MS-4 marked complete, G16 score 9/10 → 10/10"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before Batch 3
```

---

### Batch 3: Inventory Module Fixes (G17) — after Batch 2 pipeline green

#### INV-4 (MEDIUM): Image preview in ProductDetailScreen

- Images tab currently has URL text field only — no preview
- Add Coil `AsyncImage` below the URL field
- Follow existing AsyncImage usage pattern in codebase
- Show placeholder on loading, error icon on failure

#### INV-8 (LOW): Search result count in ProductListScreen

- After search, show "X products found" chip/text below search bar
- Data already in state: `state.products.size`
- Small `Text` composable with `MaterialTheme.typography.labelMedium`

#### INV-9 (LOW): Unsaved changes warning in ProductDetailScreen

- Track form dirty state (compare current values vs initial loaded values)
- On back press, show `AlertDialog`: "Discard unsaved changes?"
- Use `BackHandler` composable to intercept back navigation

**Commit:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add composeApp/feature/inventory/ todo/missing-features-implementation-plan.md
git commit -m "fix(inventory): add image preview, search count, unsaved changes warning [INV-4, INV-8, INV-9]

- INV-4: Coil AsyncImage preview in ProductDetailScreen Images tab
- INV-8: Product count chip below search bar in ProductListScreen
- INV-9: BackHandler with discard confirmation dialog in ProductDetailScreen

Plan file updated: INV-4, INV-8, INV-9 marked complete"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before Batch 4
```

---

### Batch 4: Phase 1.5 Quick Wins (G21) — after Batch 3 pipeline green

#### Hourly sparkline in Dashboard

- `DashboardViewModel` already calculates hourly data — check state for sparkline data
- Find the composable that should render it (likely `DashboardScreen`)
- Add `Canvas` composable to draw sparkline (or use existing chart component)
- Data: list of hourly revenue points → draw line graph

#### UTC offset in timezone dropdown

- Find timezone selector (likely in Settings → Store profile)
- Currently shows timezone IDs (e.g., "Asia/Colombo")
- Add UTC offset: "Asia/Colombo (UTC+5:30)"
- Use `kotlinx.datetime.TimeZone` to compute offset

**Commit:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add composeApp/feature/dashboard/ composeApp/feature/settings/ todo/missing-features-implementation-plan.md
git commit -m "feat(dashboard): render hourly sparkline and timezone UTC offset [G21 partial]

- Dashboard sparkline chart using pre-calculated hourly revenue data
- Timezone dropdown now shows UTC offset (e.g., Asia/Colombo UTC+5:30)
- G21 remaining: printer test button, Remember Me, employee badge (deferred)

Plan file updated: G21 2 of 5 quick wins done"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green
```

---

## Architecture Rules (MUST follow for ALL UI work)

### MVI Compliance
- ALL screens use MVI: `State` (immutable) + `Intent` (sealed class) + `Effect` (one-shot)
- Every ViewModel MUST extend `BaseViewModel<State, Intent, Effect>` (ADR-001)
- For new UI elements on existing screens: ADD new Intent to existing ViewModel — do NOT create new VMs
- State mutations ONLY via `updateState { copy(...) }` — never modify state directly
- Side effects (navigation, toasts) ONLY via `sendEffect()` — never from composable

### DRY Rule
- Use `Zynta*` design system components where available (27+ exist)
- Use `ZyntaEmptyState`, `ZyntaSearchBar`, `ZyntaCard`, `ZyntaButton` etc.
- Use `CurrencyUtils`, `DateTimeUtils`, `ValidationUtils` from `:shared:core`
- NEVER create a new utility if one already exists in the codebase

### Responsive Design
- Use `WindowSizeClass` for breakpoint-aware layouts
- Follow existing responsive patterns in the same module

### Accessibility
- Always add `contentDescription` to icons and images
- Use semantic labels on interactive elements

---

## Post-Implementation Updates (MANDATORY — per batch, in SAME commit)

### Update `todo/missing-features-implementation-plan.md` after EACH batch:

1. Mark `[x]` on completed items in G16, G17, G18, G21 sections
2. Update module audit scores if improved (e.g., G16 score 9/10 → 10/10)
3. Update G21 Phase 1.5 Quick Wins checklist
4. Update FEATURE COVERAGE MATRIX at bottom

### Update `CLAUDE.md`: **DO NOT update CLAUDE.md** — Stream 2 owns CLAUDE.md updates to avoid merge conflicts.

If route count changed or new design system components were created, leave a note in
your plan file HANDOFF section. Stream 2 or a follow-up session will consolidate.

---

## ⚠️ Plan File Merge Conflict Warning

All 4 parallel streams update `todo/missing-features-implementation-plan.md`.
**Merge conflicts on this file are expected and normal.**

After EVERY push, check PR status:
```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "
import sys,json
prs=json.load(sys.stdin)
if not prs: print('No open PR yet')
for pr in prs:
  print(f'PR #{pr[\"number\"]}: mergeable={pr.get(\"mergeable\")} state={pr.get(\"mergeable_state\")}')
"
```

**If `mergeable=false` or `mergeable_state=dirty`:**
```bash
git fetch origin main
git merge origin/main --no-edit
# If plan file conflicts: keep BOTH your changes AND main's changes
# (they modify different sections of the same file)
git add todo/missing-features-implementation-plan.md
git commit -m "merge: resolve plan file conflict with main"
git push -u origin $(git branch --show-current)
```

---

## Pipeline Monitoring (after EVERY push)

```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)

# Step[1] — Branch Validate
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('workflow_runs',[])]"

# Step[2] — Confirm PR
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "import sys,json; prs=json.load(sys.stdin); print('PR #'+str(prs[0]['number']) if prs else 'No PR yet')"

# Step[3+4] — CI Gate
PR=<number>
SHA=$(curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls/$PR" | python3 -c "import sys,json; print(json.load(sys.stdin)['head']['sha'])")
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/commits/$SHA/check-runs" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('check_runs',[])]"
```

**CRITICAL:** Do NOT start next batch until current batch's pipeline is green.
**CRITICAL:** Do NOT end session without final push + pipeline green verification.

---

## Important Notes

- These items do NOT touch backend code or `:shared:domain` models
  (minimal conflict risk with Streams 1, 2, 4)
- For navigation changes: test that existing routes still work (no regressions)
- For ViewModel changes: add Intent to EXISTING sealed class — don't create new VMs
- Compose UI changes may trigger Android Lint warnings — fix them before pushing
- If `@Composable` scope errors occur, check ERROR RECOVERY GUIDE in plan file
- For Coil `AsyncImage`: check existing usage patterns before implementing
