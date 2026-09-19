#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_demo-lib.sh
source "${SCRIPT_DIR}/_demo-lib.sh"

FRAUD_SERVICE_NAME="${FRAUD_SERVICE_NAME:-fraud-service}"
FRAUD_HEALTH_URL="${FRAUD_HEALTH_URL:-http://localhost:8084/actuator/health}"
AMOUNT_MINOR="${AMOUNT_MINOR:-2599}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-demo-fraud-$(date +%s)}"

fraud_started=false
cleanup() {
  if [[ "$fraud_started" == false ]]; then
    log "Restoring ${FRAUD_SERVICE_NAME}"
    compose start "$FRAUD_SERVICE_NAME" >/dev/null || true
  fi
}
trap cleanup EXIT

require_commands curl jq docker
acquire_token
docker compose version >/dev/null 2>&1 ||
  fail "Docker Compose v2 is required"

log "Stopping ${FRAUD_SERVICE_NAME} to force degraded fraud handling"
compose stop "$FRAUD_SERVICE_NAME" >/dev/null

create_payment "$AMOUNT_MINOR" "$IDEMPOTENCY_KEY" "FRAUD-OUTAGE-DEMO"
assert_curl_ok
assert_http_in "200,201,202"
assert_json_field_in '.status' "PENDING_REVIEW"
assert_json_field_in '.fraudDecision' "REVIEW"
payment_id="$(jq -er '.paymentId // .id' <<<"$RESPONSE_BODY")" ||
  fail "payment response did not contain paymentId or id"
log "Payment $payment_id is held in PENDING_REVIEW with funds reserved"

log "Restarting ${FRAUD_SERVICE_NAME}"
compose start "$FRAUD_SERVICE_NAME" >/dev/null
fraud_started=true
wait_for_url "$FRAUD_HEALTH_URL"

api_request GET "/api/v1/payments/${payment_id}" ""
assert_curl_ok
assert_http_in "200"
assert_json_field_in '.status' "PENDING_REVIEW"
log "Fraud outage/recovery demo passed for payment $payment_id"
