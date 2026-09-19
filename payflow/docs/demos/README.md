
# PayNexus resilience demos

These scripts exercise PayNexus's integer-money API (`amountMinor`) and
fail loudly when a resilience guarantee is not met. They are executable
documentation: each script enables Bash strict mode, checks its dependencies,
obtains a Keycloak token, and asserts response codes and business state.

Run the scripts from any directory. Docker-based fault injection is resolved
against the repository root automatically.

## Prerequisites

- PayNexus and its infrastructure are running.
- Bash 3.2 or newer, `curl`, and `jq` are installed.
- Docker Compose v2 is required by the outage and Kafka DLT demos.
- `openssl` is required by the tracing demo.
- The caller has a Keycloak test user authorized to create and read payments.
- The payment contract accepts `amountMinor`; decimal `amount` is not accepted.

Make the scripts executable once:

```bash
chmod +x docs/demos/*.sh
```

## Authentication and URLs

Every script accepts a pre-issued token:

```bash
ACCESS_TOKEN='eyJ...' docs/demos/idempotent-replay.sh
```

Otherwise it uses the Keycloak resource-owner password flow. This flow is
appropriate only for local demos, and the client must have Direct Access Grants
enabled:

```bash
export KEYCLOAK_BASE_URL=http://localhost:8088
export KEYCLOAK_REALM=paynexus
export KEYCLOAK_CLIENT_ID=paynexus-cli
export KEYCLOAK_CLIENT_SECRET='' # optional for a public client
export KEYCLOAK_USERNAME=demo@paynexus.local
export KEYCLOAK_PASSWORD='Demo123!'
export API_BASE_URL=http://localhost:8080
```

Common optional settings are `HTTP_CONNECT_TIMEOUT_SECONDS`,
`HTTP_MAX_TIME_SECONDS`, `AMOUNT_MINOR`, and `PROJECT_ROOT`. Service-specific
URLs and Compose service names are documented by the defaults at the top of
each script.

## Scenarios

### Fraud outage and recovery

```bash
docs/demos/fraud-outage-recovery.sh
```

Stops the fraud service, creates a payment, and verifies degraded-mode policy:
the payment stays `PENDING_REVIEW` with funds reserved until an analyst decides.
It then restarts fraud and confirms the held payment is unchanged.

### Wallet timeout and circuit fail-fast

```bash
docs/demos/wallet-timeout-fail-fast.sh
```

Pauses the wallet container to create a genuine socket timeout, sends enough
unique payments to open the wallet circuit, and asserts the next call fails
within `FAIL_FAST_MAX_SECONDS`. A trap always unpauses the container.

### Idempotent payment replay

```bash
docs/demos/idempotent-replay.sh
```

Posts the same body and `Idempotency-Key` twice. Both responses must expose the
same payment identifier and `amountMinor`.

### Notification dead-letter topic

```bash
docs/demos/notification-dlt.sh
```

Publishes a uniquely identified notification that violates a required business
field, waits through retry backoff, and asserts that the same event appears on
`notification.send.DLT`. Topic and Compose service names are configurable.

### End-to-end sampled trace

```bash
docs/demos/traced-payment.sh
```

Creates a W3C sampled `traceparent`, submits a payment, and polls the Jaeger
query API until that exact trace contains spans. Set `JAEGER_QUERY_URL` when
Jaeger is not exposed at `http://localhost:16686`.

## HTTP-client walkthrough

`docs/resilience.http` provides equivalent request-oriented examples for token
acquisition, idempotency, review status, and explicit trace propagation. Fault
injection and DLT inspection remain in Bash because they require Docker/Kafka
commands and cleanup traps.
