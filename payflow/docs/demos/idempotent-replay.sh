#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_demo-lib.sh
source "${SCRIPT_DIR}/_demo-lib.sh"

AMOUNT_MINOR="${AMOUNT_MINOR:-1499}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-demo-replay-$(date +%s)}"

require_commands curl jq
acquire_token

log "Creating payment with idempotency key '$IDEMPOTENCY_KEY'"
create_payment "$AMOUNT_MINOR" "$IDEMPOTENCY_KEY" "IDEMPOTENCY-DEMO"
assert_curl_ok
assert_http_in "200,201,202"
first_response="$RESPONSE_BODY"
first_payment_id="$(jq -er '.paymentId // .id' <<<"$first_response")" ||
  fail "first response did not contain paymentId or id"

log "Replaying the exact request with the same idempotency key"
create_payment "$AMOUNT_MINOR" "$IDEMPOTENCY_KEY" "IDEMPOTENCY-DEMO"
assert_curl_ok
assert_http_in "200,201,202"
second_payment_id="$(jq -er '.paymentId // .id' <<<"$RESPONSE_BODY")" ||
  fail "replay response did not contain paymentId or id"

[[ "$second_payment_id" == "$first_payment_id" ]] ||
  fail "replay created a different payment: $first_payment_id != $second_payment_id"

first_amount="$(jq -er '.amountMinor' <<<"$first_response")" ||
  fail "first response did not expose amountMinor"
second_amount="$(jq -er '.amountMinor' <<<"$RESPONSE_BODY")" ||
  fail "replay response did not expose amountMinor"
[[ "$first_amount" == "$AMOUNT_MINOR" && "$second_amount" == "$AMOUNT_MINOR" ]] ||
  fail "amountMinor changed across replay"

log "Idempotency assertion passed: both responses identify payment $first_payment_id"
