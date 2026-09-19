#!/usr/bin/env bash
set -Eeuo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd -- "${SCRIPT_DIR:-$(pwd)}/../.." 2>/dev/null && pwd)}"
KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8088}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-paynexus}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-paynexus-cli}"
KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-}"
KEYCLOAK_USERNAME="${KEYCLOAK_USERNAME:-}"
KEYCLOAK_PASSWORD="${KEYCLOAK_PASSWORD:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
HTTP_CONNECT_TIMEOUT_SECONDS="${HTTP_CONNECT_TIMEOUT_SECONDS:-3}"
HTTP_MAX_TIME_SECONDS="${HTTP_MAX_TIME_SECONDS:-20}"

API_BASE_URL="${API_BASE_URL%/}"
KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL%/}"

log() {
  printf '[demo] %s\n' "$*"
}

fail() {
  printf '[demo] ASSERTION FAILED: %s\n' "$*" >&2
  exit 1
}

require_commands() {
  local command_name
  for command_name in "$@"; do
    command -v "$command_name" >/dev/null 2>&1 ||
      fail "required command '$command_name' was not found"
  done
}

require_env() {
  local variable_name
  for variable_name in "$@"; do
    [[ -n "${!variable_name:-}" ]] ||
      fail "set $variable_name (or provide ACCESS_TOKEN where authentication is required)"
  done
}

acquire_token() {
  require_commands curl jq
  if [[ -n "$ACCESS_TOKEN" ]]; then
    log "Using ACCESS_TOKEN supplied by the caller"
    return
  fi

  require_env KEYCLOAK_USERNAME KEYCLOAK_PASSWORD
  local token_url response_file status
  token_url="${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"
  response_file="$(mktemp)"

  local -a form=(
    --data-urlencode "grant_type=password"
    --data-urlencode "client_id=${KEYCLOAK_CLIENT_ID}"
    --data-urlencode "username=${KEYCLOAK_USERNAME}"
    --data-urlencode "password=${KEYCLOAK_PASSWORD}"
  )
  if [[ -n "$KEYCLOAK_CLIENT_SECRET" ]]; then
    form+=(--data-urlencode "client_secret=${KEYCLOAK_CLIENT_SECRET}")
  fi

  status="$(curl --silent --show-error \
    --connect-timeout "$HTTP_CONNECT_TIMEOUT_SECONDS" \
    --max-time "$HTTP_MAX_TIME_SECONDS" \
    --output "$response_file" --write-out '%{http_code}' \
    --request POST "$token_url" "${form[@]}")"

  [[ "$status" == "200" ]] ||
    fail "Keycloak token request returned HTTP $status: $(<"$response_file")"
  ACCESS_TOKEN="$(jq -er '.access_token' "$response_file")" ||
    fail "Keycloak response did not contain access_token"
  rm -f "$response_file"
  log "Acquired an access token from realm '$KEYCLOAK_REALM'"
}

api_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  shift 3 || true

  RESPONSE_FILE="$(mktemp)"
  local -a args=(
    --silent --show-error
    --connect-timeout "$HTTP_CONNECT_TIMEOUT_SECONDS"
    --max-time "$HTTP_MAX_TIME_SECONDS"
    --output "$RESPONSE_FILE"
    --write-out '%{http_code}'
    --request "$method"
    --header "Authorization: Bearer ${ACCESS_TOKEN}"
    --header 'Accept: application/json'
  )
  if [[ -n "$body" ]]; then
    args+=(--header 'Content-Type: application/json' --data "$body")
  fi
  args+=("$@")

  set +e
  HTTP_STATUS="$(curl "${args[@]}" "${API_BASE_URL}${path}")"
  CURL_EXIT_CODE=$?
  set -e
  RESPONSE_BODY="$(<"$RESPONSE_FILE")"
  rm -f "$RESPONSE_FILE"
}

payment_payload() {
  local amount_minor="$1"
  local merchant_id="${2:-DEMO-MERCHANT}"
  local currency="${3:-USD}"
  jq -cn \
    --argjson amountMinor "$amount_minor" \
    --arg currency "$currency" \
    --arg merchantId "$merchant_id" \
    '{amountMinor: $amountMinor, currency: $currency, merchantId: $merchantId}'
}

create_payment() {
  local amount_minor="$1"
  local idempotency_key="$2"
  local merchant_id="${3:-DEMO-MERCHANT}"
  local traceparent="${4:-}"
  local payload
  payload="$(payment_payload "$amount_minor" "$merchant_id")"

  local -a headers=(--header "Idempotency-Key: ${idempotency_key}")
  if [[ -n "$traceparent" ]]; then
    headers+=(--header "traceparent: ${traceparent}")
  fi
  api_request POST /api/v1/payments "$payload" "${headers[@]}"
}

assert_curl_ok() {
  [[ "$CURL_EXIT_CODE" -eq 0 ]] ||
    fail "curl exited with $CURL_EXIT_CODE (HTTP status ${HTTP_STATUS:-unknown})"
}

assert_http_in() {
  local expected_csv="$1"
  local value
  IFS=',' read -r -a expected_values <<<"$expected_csv"
  for value in "${expected_values[@]}"; do
    if [[ "$HTTP_STATUS" == "$value" ]]; then
      log "Assertion passed: HTTP $HTTP_STATUS"
      return
    fi
  done
  fail "expected HTTP {$expected_csv}, got ${HTTP_STATUS}: ${RESPONSE_BODY}"
}

assert_json_field_in() {
  local expression="$1"
  local expected_csv="$2"
  local actual expected
  actual="$(jq -er "$expression" <<<"$RESPONSE_BODY")" ||
    fail "response has no value for jq expression '$expression': $RESPONSE_BODY"
  IFS=',' read -r -a expected_values <<<"$expected_csv"
  for expected in "${expected_values[@]}"; do
    if [[ "$actual" == "$expected" ]]; then
      log "Assertion passed: $expression is '$actual'"
      return
    fi
  done
  fail "expected $expression in {$expected_csv}, got '$actual'"
}

compose() {
  docker compose --project-directory "$PROJECT_ROOT" "$@"
}

wait_for_url() {
  local url="$1"
  local attempts="${2:-30}"
  local delay_seconds="${3:-2}"
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --silent --fail --max-time 2 "$url" >/dev/null 2>&1; then
      log "Ready: $url"
      return
    fi
    sleep "$delay_seconds"
  done
  fail "timed out waiting for $url"
}
