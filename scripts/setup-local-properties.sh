#!/usr/bin/env bash
# =============================================================================
# setup-local-properties.sh
#
# Generates local.properties from the dev template for CI and new developer
# machine setup. No production secrets are needed for debug builds.
#
# Usage:
#   # CI (GitHub Actions) — ANDROID_SDK_ROOT is set by the runner environment
#   ./scripts/setup-local-properties.sh
#
#   # Local developer machine — override sdk path if needed
#   ANDROID_SDK_ROOT=/path/to/sdk ./scripts/setup-local-properties.sh
#
# What this script does:
#   1. Copies local.properties.dev → local.properties
#   2. Replaces the placeholder sdk.dir with the actual ANDROID_SDK_ROOT value
#
# The dev template contains safe, clearly-labeled placeholder values for all
# secrets. In debug builds, ApiService is replaced by DevApiService (no-op stub),
# so API credentials are never actually used.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DEV_TEMPLATE="${REPO_ROOT}/local.properties.dev"
LOCAL_PROPS="${REPO_ROOT}/local.properties"

# ── Validate template exists ──────────────────────────────────────────────────
if [[ ! -f "${DEV_TEMPLATE}" ]]; then
    echo "ERROR: ${DEV_TEMPLATE} not found." >&2
    echo "       Make sure you are running this from the repository root." >&2
    exit 1
fi

# ── Copy template ─────────────────────────────────────────────────────────────
cp "${DEV_TEMPLATE}" "${LOCAL_PROPS}"
echo "Copied local.properties.dev → local.properties"

# ── Substitute sdk.dir ────────────────────────────────────────────────────────
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ -n "${SDK_DIR}" ]]; then
    # Escape forward slashes for sed
    SDK_ESCAPED="${SDK_DIR//\//\\/}"
    sed -i "s|sdk\.dir=.*|sdk.dir=${SDK_ESCAPED}|g" "${LOCAL_PROPS}"
    echo "Set sdk.dir=${SDK_DIR}"
else
    echo "WARNING: ANDROID_SDK_ROOT is not set — sdk.dir left as placeholder." >&2
    echo "         Update sdk.dir in local.properties manually before building." >&2
fi

echo "Done. local.properties is ready for debug builds."
