#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_demo-lib.sh
source "${SCRIPT_DIR}/_demo-lib.sh"

WALLET_SERVICE_NAME="${WALLET_SERVICE_NAME:-wallet-service}"
WALLET_HEALTH_URL="${WALLET_HEALTH_URL:-http://localhost:8083/actuator/health}"
AMOUNT_MINOR="${AMOUNT_MINOR:-1099}"
CIRCUIT_WARMUP_REQUESTS="${CIRCUIT_WARMUP_REQUESTS:-5}"
FAIL_FAST_MAX_SECONDS="${FAIL_FAST_MAX_SECONDS:-2}"

wallet_paused=false
cleanup() {
  if [[ "$wallet_paused" == true ]]; then
    log "Unpausing ${WALLET_SERVICE_NAME}"
    compose unpause "$WALLET_SERVICE_NAME" >/dev/null || true
  fi
}
trap cleanup EXIT

assert_wallet_failure() {
  assert_curl_ok
  assert_http_in "200,201,202,500,502,503,504"
  if [[ "$HTTP_STATUS" =~ ^2 ]]; then
    assert_json_field_in '.status' "FAILED"
  fi
}

require_commands curl jq docker
acquire_token
docker compose version >/dev/null 2>&1 ||
  fail "Docker Compose v2 is required"

log "Pausing ${WALLET_SERVICE_NAME} to produce a real downstream timeout"
compose pause "$WALLET_SERVICE_NAME" >/dev/null
wallet_paused=true

start_seconds="$(date +%s)"
create_payment "$AMOUNT_MINOR" "demo-wallet-timeout-$(date +%s)-first" "WALLET-TIMEOUT-DEMO"
first_elapsed="$(( $(date +%s) - start_seconds ))"
assert_wallet_failure
log "Initial wallet failure returned after ${first_elapsed}s"

for ((attempt = 2; attempt <= CIRCUIT_WARMUP_REQUESTS; attempt++)); do
  create_payment "$AMOUNT_MINOR" "demo-wallet-timeout-$(date +%s)-${attempt}" "WALLET-TIMEOUT-DEMO"
  assert_wallet_failure
done

start_seconds="$(date +%s)"
create_payment "$AMOUNT_MINOR" "demo-wallet-fastfail-$(date +%s)" "WALLET-FASTFAIL-DEMO"
fast_fail_elapsed="$(( $(date +%s) - start_seconds ))"
assert_wallet_failure
(( fast_fail_elapsed <= FAIL_FAST_MAX_SECONDS )) ||
  fail "circuit did not fail fast: ${fast_fail_elapsed}s exceeds ${FAIL_FAST_MAX_SECONDS}s"
log "Circuit assertion passed: failure returned in ${fast_fail_elapsed}s"

compose unpause "$WALLET_SERVICE_NAME" >/dev/null
wallet_paused=false
wait_for_url "$WALLET_HEALTH_URL"
log "Wallet timeout/fail-fast demo passed"
