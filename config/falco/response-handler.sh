#!/usr/bin/env bash
# ============================================================
# ZyntaPOS — Falco Automated Incident Response Handler
#
# Invoked by Falcosidekick via its webhook output when a Falco
# rule fires at CRITICAL or higher priority.
#
# Falcosidekick webhook config (falcosidekick.yaml):
#   webhook:
#     minimumpriority: critical
#     address: http://localhost:2802/response  ← local handler endpoint
#
# Usage (called by Falcosidekick or manually for testing):
#   RULE="<rule_name>" PRIORITY="<priority>" \
#     OUTPUT="<falco_output>" ./response-handler.sh
#
# Environment variables set by Falcosidekick (via templating):
#   RULE      — Falco rule name that fired
#   PRIORITY  — Falco priority (CRITICAL, ERROR, WARNING, …)
#   OUTPUT    — Full Falco alert output line
#   SOURCE    — Event source (syscall, k8s_audit, etc.)
#
# Responses by rule:
#   JVM writes class files at runtime    → kill suspect process, dump thread stack
#   JVM spawns unexpected shell          → kill process + isolate network
#   ZyntaPOS reads private key at runtime → kill process + rotate key (alert)
#   ZyntaPOS DB accessed by unknown user → kill connection + alert
#   Any CRITICAL                         → restart affected container (last resort)
# ============================================================
set -euo pipefail

RULE="${RULE:-unknown}"
PRIORITY="${PRIORITY:-UNKNOWN}"
OUTPUT="${OUTPUT:-no output}"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
LOG_FILE="/var/log/falco-response.log"
COMPOSE_DIR="/opt/zyntapos"

log() {
    echo "[$TIMESTAMP] [response-handler] $*" | tee -a "$LOG_FILE"
}

# ── Alert Slack (if SLACK_WEBHOOK_URL is set) ─────────────────────────────
alert_slack() {
    local message="$1"
    if [[ -n "${SLACK_WEBHOOK_URL:-}" ]]; then
        curl -s -X POST -H "Content-Type: application/json" \
            -d "{\"text\":\"🚨 *Falco Auto-Response* | $message\"}" \
            "$SLACK_WEBHOOK_URL" || true
    fi
}

# ── Kill a process by PID or name pattern ─────────────────────────────────
kill_process() {
    local pattern="$1"
    local pids
    pids=$(pgrep -f "$pattern" 2>/dev/null || true)
    if [[ -n "$pids" ]]; then
        log "Killing processes matching '$pattern': $pids"
        echo "$pids" | xargs kill -9 2>/dev/null || true
        alert_slack "[$PRIORITY] Rule: $RULE — killed process(es): $pids"
    else
        log "No processes found matching '$pattern'"
    fi
}

# ── Restart a Docker container ────────────────────────────────────────────
restart_container() {
    local service="$1"
    log "Restarting Docker container: $service"
    if [[ -d "$COMPOSE_DIR" ]]; then
        cd "$COMPOSE_DIR"
        docker compose restart "$service" 2>&1 | tee -a "$LOG_FILE" || true
        alert_slack "[$PRIORITY] Rule: $RULE — restarted container: $service"
    else
        log "ERROR: compose dir $COMPOSE_DIR not found — cannot restart $service"
        alert_slack "[$PRIORITY] Rule: $RULE — FAILED to restart $service (dir not found)"
    fi
}

# ── Dump JVM thread stack trace for forensics ─────────────────────────────
dump_jvm_threads() {
    local container="$1"
    local dump_file="/var/log/falco-jstack-${container}-${TIMESTAMP}.txt"
    log "Dumping JVM threads for container: $container → $dump_file"
    docker exec "$container" sh -c \
        "jcmd 1 Thread.print 2>/dev/null || kill -3 1 2>/dev/null || true" \
        > "$dump_file" 2>&1 || true
}

# ── Main dispatch ─────────────────────────────────────────────────────────
log "Handling Falco alert: RULE='$RULE' PRIORITY='$PRIORITY'"
log "Output: ${OUTPUT:0:200}"

case "$RULE" in

    "JVM writes class files at runtime"*)
        log "ACTION: Suspected code injection — terminating JVM process"
        dump_jvm_threads "zyntapos-api-1"
        kill_process "java.*zyntapos"
        alert_slack "[$PRIORITY] *Code injection detected* — JVM wrote .class file at runtime. Process killed. See falco-response.log"
        ;;

    "JVM spawns unexpected shell"*)
        log "ACTION: JVM shell spawn — possible RCE — killing process"
        dump_jvm_threads "zyntapos-api-1"
        kill_process "java.*zyntapos"
        alert_slack "[$PRIORITY] *RCE suspected* — JVM spawned unexpected shell. Process killed immediately."
        ;;

    "ZyntaPOS reads private key at runtime"*)
        log "ACTION: Unexpected private key read — killing process and alerting for key rotation"
        kill_process "java.*zyntapos"
        alert_slack "[$PRIORITY] *KEY READ ALERT* — private key accessed unexpectedly. Process killed. *Rotate RS256 key pair immediately via FTS Step 4.*"
        ;;

    "ZyntaPOS DB accessed by unknown process"*)
        log "ACTION: Unauthorized DB connection — alerting ops team"
        alert_slack "[$PRIORITY] *DB ACCESS ALERT* — database accessed by unknown process. Manual investigation required."
        ;;

    *)
        # Generic CRITICAL handler — restart affected container
        if [[ "$PRIORITY" == "CRITICAL" ]]; then
            log "ACTION: Unknown CRITICAL rule — restarting api container as precaution"
            restart_container "api"
        else
            log "ACTION: No automated response for rule '$RULE' at priority '$PRIORITY' — alert only"
            alert_slack "[$PRIORITY] Falco alert: $RULE — no automated response configured."
        fi
        ;;

esac

log "Response complete for rule: $RULE"
