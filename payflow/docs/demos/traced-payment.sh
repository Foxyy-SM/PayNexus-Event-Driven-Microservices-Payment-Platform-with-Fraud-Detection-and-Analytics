#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_demo-lib.sh
source "${SCRIPT_DIR}/_demo-lib.sh"

JAEGER_QUERY_URL="${JAEGER_QUERY_URL:-http://localhost:16686}"
AMOUNT_MINOR="${AMOUNT_MINOR:-3299}"
TRACE_WAIT_ATTEMPTS="${TRACE_WAIT_ATTEMPTS:-30}"
TRACE_WAIT_SECONDS="${TRACE_WAIT_SECONDS:-2}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-demo-trace-$(date +%s)}"

require_commands curl jq openssl
acquire_token

trace_id="$(openssl rand -hex 16)"
parent_span_id="$(openssl rand -hex 8)"
traceparent="00-${trace_id}-${parent_span_id}-01"

log "Creating a sampled payment with trace ID $trace_id"
create_payment "$AMOUNT_MINOR" "$IDEMPOTENCY_KEY" "TRACING-DEMO" "$traceparent"
assert_curl_ok
assert_http_in "200,201,202"
payment_id="$(jq -er '.paymentId // .id' <<<"$RESPONSE_BODY")" ||
  fail "payment response did not contain paymentId or id"

jaeger_response_file="$(mktemp)"
trap 'rm -f "$jaeger_response_file"' EXIT
for ((attempt = 1; attempt <= TRACE_WAIT_ATTEMPTS; attempt++)); do
  if curl --silent --show-error --fail \
    --connect-timeout "$HTTP_CONNECT_TIMEOUT_SECONDS" \
    --max-time "$HTTP_MAX_TIME_SECONDS" \
    --output "$jaeger_response_file" \
    "${JAEGER_QUERY_URL%/}/api/traces/${trace_id}" &&
    jq -e --arg traceId "$trace_id" \
      '.data | any(.traceID == $traceId) and any(.spans | length > 0)' \
      "$jaeger_response_file" >/dev/null; then
    log "Trace assertion passed for payment $payment_id"
    log "Jaeger: ${JAEGER_QUERY_URL%/}/trace/${trace_id}"
    exit 0
  fi
  sleep "$TRACE_WAIT_SECONDS"
done

fail "trace $trace_id did not appear in Jaeger after $((TRACE_WAIT_ATTEMPTS * TRACE_WAIT_SECONDS))s"
