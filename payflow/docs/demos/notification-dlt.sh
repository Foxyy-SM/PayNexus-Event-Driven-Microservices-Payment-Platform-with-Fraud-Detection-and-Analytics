#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_demo-lib.sh
source "${SCRIPT_DIR}/_demo-lib.sh"

KAFKA_SERVICE_NAME="${KAFKA_SERVICE_NAME:-kafka}"
NOTIFICATION_TOPIC="${NOTIFICATION_TOPIC:-notification.send}"
NOTIFICATION_DLT_TOPIC="${NOTIFICATION_DLT_TOPIC:-notification.send.DLT}"
DLT_WAIT_SECONDS="${DLT_WAIT_SECONDS:-12}"

require_commands curl jq docker grep python3
acquire_token
docker compose version >/dev/null 2>&1 ||
  fail "Docker Compose v2 is required"

event_id="$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"
event="$(jq -cn \
  --arg eventId "$event_id" \
  --arg occurredAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{
    eventId: $eventId,
    userId: null,
    channel: "EMAIL",
    template: "DLT_DEMO",
    destination: "demo@example.invalid",
    payloadJson: "{}",
    occurredAt: $occurredAt
  }')"

log "Publishing poison notification $event_id to $NOTIFICATION_TOPIC"
printf '__TypeId__:com.payflow.events.NotificationRequestedEvent\t%s\n' "$event" |
  compose exec -T "$KAFKA_SERVICE_NAME" \
    /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic "$NOTIFICATION_TOPIC" \
    --property parse.headers=true \
    --property headers.delimiter=$'\t' >/dev/null

log "Waiting ${DLT_WAIT_SECONDS}s for retries and DLT publication"
sleep "$DLT_WAIT_SECONDS"

set +e
dlt_records="$(
  compose exec -T "$KAFKA_SERVICE_NAME" \
    /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic "$NOTIFICATION_DLT_TOPIC" \
    --from-beginning \
    --timeout-ms 10000 2>/dev/null
)"
consumer_exit=$?
set -e

[[ "$consumer_exit" -eq 0 || "$consumer_exit" -eq 1 ]] ||
  fail "Kafka DLT consumer exited with $consumer_exit"
grep -Fq "$event_id" <<<"$dlt_records" ||
  fail "event $event_id was not found on $NOTIFICATION_DLT_TOPIC"

log "DLT assertion passed: event $event_id reached $NOTIFICATION_DLT_TOPIC"
