#!/usr/bin/env bash
# =============================================================================
# generate-tls-signing-key.sh — ZyntaPOS TLS Signed Pin List (ADR-011)
#
# Generates an Ed25519 signing keypair and signs the current TLS pin list for
# api.zyntapos.com. Run this script:
#   1. After generating a new Ed25519 keypair (initial setup or key rotation).
#   2. After every Caddy certificate renewal that changes the leaf SPKI pin.
#
# Prerequisites: openssl (1.1.1+, built with ed25519 support)
#
# Usage:
#   # Generate a new keypair (do this ONCE; store the private key securely offline)
#   ./scripts/generate-tls-signing-key.sh --keygen
#
#   # Sign a pin list (run after each cert renewal)
#   ./scripts/generate-tls-signing-key.sh --sign \
#     --private-key /path/to/pin-signing-key.pem \
#     --pins "sha256/LEAF_PIN_HERE,sha256/BACKUP_PIN_HERE" \
#     --expires "2026-09-01T00:00:00Z"
#
# Output:
#   --keygen: Prints the Base64 DER public key. Update API_PIN_SIGNING_PUBLIC_KEY
#             in shared/data/src/commonMain/.../CertificatePinConstants.kt.
#
#   --sign:   Prints the signed JSON blob. Set it as TLS_PINS_JSON in docker-compose
#             or write it to the file pointed to by TLS_PINS_JSON_PATH.
#
# Security:
#   - Keep the Ed25519 private key OFFLINE (never in the Docker environment).
#   - Run --sign on an air-gapped machine, then copy the JSON to the server.
#   - The signed JSON blob is non-secret and can be stored as a plaintext env var.
# =============================================================================

set -euo pipefail

usage() {
    echo "Usage:"
    echo "  $0 --keygen"
    echo "  $0 --sign --private-key <pem-file> --pins <csv-pins> --expires <iso8601>"
    exit 1
}

# ── Key generation ────────────────────────────────────────────────────────────

do_keygen() {
    local key_file="/tmp/pin-signing-key.pem"
    local pub_file="/tmp/pin-signing-pub.pem"

    echo "Generating Ed25519 keypair..."
    openssl genpkey -algorithm ed25519 -out "$key_file"

    echo ""
    echo "=== PRIVATE KEY (keep this OFFLINE and SECURE) ==="
    cat "$key_file"

    echo ""
    echo "=== PUBLIC KEY — DER Base64 (X.509 SubjectPublicKeyInfo) ==="
    echo "Update API_PIN_SIGNING_PUBLIC_KEY in CertificatePinConstants.kt with this value:"
    echo ""
    openssl pkey -pubout -outform der -in "$key_file" | base64 -w 0
    echo ""
    echo ""
    echo "Public key also saved to: $pub_file"
    openssl pkey -pubout -in "$key_file" -out "$pub_file"
}

# ── Signing ───────────────────────────────────────────────────────────────────

do_sign() {
    local private_key_file="${PRIVATE_KEY_FILE:-}"
    local pins_csv="${PINS_CSV:-}"
    local expires_at="${EXPIRES_AT:-}"

    if [[ -z "$private_key_file" || -z "$pins_csv" || -z "$expires_at" ]]; then
        echo "Error: --sign requires --private-key, --pins, and --expires" >&2
        usage
    fi

    if [[ ! -f "$private_key_file" ]]; then
        echo "Error: private key file not found: $private_key_file" >&2
        exit 1
    fi

    # Split CSV pins into array and sort them
    IFS=',' read -ra pins_array <<< "$pins_csv"
    local sorted_pins
    sorted_pins=$(printf '%s\n' "${pins_array[@]}" | sort)

    # Build canonical message: sorted_pin[0]\nsorted_pin[1]\n...\nexpires_at
    local message
    message=$(printf '%s\n' "${pins_array[@]}" | sort | tr -d ' ')
    message="${message}"$'\n'"${expires_at}"

    # Sign the message
    local sig_b64
    sig_b64=$(printf '%s' "$message" | openssl pkeyutl -sign -inkey "$private_key_file" | base64 -w 0)

    # Build JSON pins array
    local pins_json
    pins_json=$(printf '%s\n' "${pins_array[@]}" | sort | sed 's/.*/"&"/' | paste -sd ',' -)
    pins_json="[${pins_json}]"

    # Output signed JSON
    local json
    json=$(cat <<EOF
{
  "pins": ${pins_json},
  "expires_at": "${expires_at}",
  "signature": "${sig_b64}"
}
EOF
)

    echo ""
    echo "=== Signed TLS Pin List JSON ==="
    echo "Set this as TLS_PINS_JSON in your .env or docker-compose.yml:"
    echo ""
    echo "$json"
    echo ""
    echo "=== Compact form (for env var) ==="
    echo "$json" | tr -d '\n' | sed 's/  */ /g'
    echo ""
}

# ── Extract current leaf pin from live server ──────────────────────────────

extract_current_pins() {
    local host="${1:-api.zyntapos.com}"
    echo ""
    echo "=== Current SPKI pins for $host ==="
    echo ""

    echo "LEAF (primary):"
    openssl s_client -connect "${host}:443" </dev/null 2>/dev/null \
        | openssl x509 -pubkey -noout \
        | openssl pkey -pubin -outform der \
        | openssl dgst -sha256 -binary \
        | base64 | sed 's/^/sha256\//'

    echo ""
    echo "INTERMEDIATE (backup — cert #2 in chain):"
    openssl s_client -connect "${host}:443" -showcerts </dev/null 2>/dev/null \
        | awk '/BEGIN CERTIFICATE/{n++} n==2,/END CERTIFICATE/ && n==2' \
        | openssl x509 -pubkey -noout \
        | openssl pkey -pubin -outform der \
        | openssl dgst -sha256 -binary \
        | base64 | sed 's/^/sha256\//'
}

# ── Argument parsing ──────────────────────────────────────────────────────────

MODE=""
PRIVATE_KEY_FILE=""
PINS_CSV=""
EXPIRES_AT=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --keygen)      MODE="keygen"; shift ;;
        --sign)        MODE="sign"; shift ;;
        --extract)     MODE="extract"; shift ;;
        --private-key) PRIVATE_KEY_FILE="$2"; shift 2 ;;
        --pins)        PINS_CSV="$2"; shift 2 ;;
        --expires)     EXPIRES_AT="$2"; shift 2 ;;
        --host)        HOST="$2"; shift 2 ;;
        -h|--help)     usage ;;
        *) echo "Unknown option: $1" >&2; usage ;;
    esac
done

case "$MODE" in
    keygen)  do_keygen ;;
    sign)    do_sign ;;
    extract) extract_current_pins "${HOST:-api.zyntapos.com}" ;;
    *) usage ;;
esac
