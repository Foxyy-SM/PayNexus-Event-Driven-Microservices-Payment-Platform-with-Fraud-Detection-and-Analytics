# PayNexus Analytics Service

Python 3.12 · FastAPI · Kafka · PostgreSQL · SQLAlchemy · pandas · pytest · Power BI-ready exports

This service is the read-only data plane for PayNexus. The Java services remain the transactional system
of record for users, payments, fraud decisions, and wallet accounting. Analytics consumes their versioned
Kafka events, lands immutable raw records, builds typed staging projections and pandas/SQL marts, then
serves operator-only metrics through a Keycloak-protected REST API.

It demonstrates the complete backend-data workflow recruiters ask for: structured and unstructured
ingestion, idempotent processing, relational modelling, dataframe transformations, API delivery,
automated testing, containerization, CI, and a Power BI consumption contract.

## Architecture

```text
Java transactional outboxes
  └─ Kafka: payment.*, fraud.checked, wallet.ledger
       └─ Python consumer group: paynexus-analytics-v1
            ├─ stg_raw_events              immutable, idempotent landing
            ├─ stg_payments                latest payment projection
            ├─ stg_fraud_assessments       risk decision projection
            ├─ stg_wallet_ledger           append-only money evidence
            └─ pandas + SQL transforms
                 ├─ mart_daily_kpis
                 └─ mart_risk_by_rule

Markdown analyst notes ── parser ── stg_analyst_notes

FastAPI :8087 ── Keycloak ADMIN ── REST/CSV ── Power BI or another BI client
```

The service never writes to a Java service database. If analytics is unavailable, payment authorization
continues normally; Kafka retention allows the consumer to catch up later.

## Data contracts

Subscribed topics:

- `payment.initiated`
- `payment.reserved`
- `payment.pending-review`
- `payment.completed`
- `payment.failed`
- `fraud.checked`
- `wallet.ledger`

Every event is first written to `stg_raw_events`. `eventId` is the primary idempotency key; the
`(topic, partition, offset)` unique constraint is a second replay guard. Kafka offsets are committed only
after the database transaction succeeds.

Money remains integer `amountMinor`. The analytics layer does not introduce floating-point currency.
Payment projection also uses a monotonic status rank, so a late `INITIATED` event can enrich missing
merchant/country fields but cannot regress an already `COMPLETED`, `FAILED`, or `REJECTED` payment.

### Structured ingestion

Kafka JSON is projected into typed payment, fraud, and ledger tables. Replaying an event does not create
another fact. A `COMPLETED` payment can therefore be compared with append-only `CAPTURE` evidence.

### Unstructured ingestion

Markdown files under `inbox/` represent analyst observations. Optional header lines identify
`payment_id`, `merchant_id`, and `reason_code`; all remaining prose is preserved as unstructured text.
The SHA-256 hash of filename plus content makes file ingestion idempotent.

Example:

```markdown
payment_id: 8d8...
merchant_id: WIRE-US
reason_code: ENHANCED_DUE_DILIGENCE

Customer confirmed travel, but source-of-funds evidence is still required.
```

## Warehouse tables

| Layer | Table | Purpose |
| --- | --- | --- |
| Landing | `stg_raw_events` | Auditable original JSON plus Kafka coordinates |
| Staging | `stg_payments` | Current status, amount, currency, merchant, country |
| Staging | `stg_fraud_assessments` | Score, decision, and human-readable explanation |
| Staging | `stg_wallet_ledger` | Reserve/capture/release evidence |
| Staging | `stg_analyst_notes` | Parsed metadata plus free text |
| Mart | `mart_daily_kpis` | Daily/currency volume, capture rate, review rate, failures |
| Mart | `mart_risk_by_rule` | Rule frequency, average score, review/reject counts |

Marts refresh every `ANALYTICS_REFRESH_SECONDS` (30 by default) and can also be refreshed by an admin.
The transformations are deterministic functions accepting row dictionaries and returning DataFrames,
which keeps them directly testable.

`sql/warehouse_queries.sql` contains portable set-based versions of daily outcomes, ledger integrity, and
ten-minute velocity analysis. It provides a direct PostgreSQL/Snowflake interview artifact alongside the
pandas implementation.

## REST API

FastAPI OpenAPI is available directly at `http://localhost:8087/docs`. Business endpoints require a
Keycloak token containing the `ADMIN` realm role.

| Method | Endpoint | Result |
| --- | --- | --- |
| `GET` | `/health` | Public liveness |
| `GET` | `/ready` | Database readiness |
| `GET` | `/api/v1/analytics/kpis?from=&to=` | Daily business KPIs |
| `GET` | `/api/v1/analytics/risk-summary` | Decisions and risk-rule distribution |
| `GET` | `/api/v1/analytics/ledger-integrity` | Completed payments missing exact capture evidence |
| `GET` | `/api/v1/analytics/pipeline-status` | Counts, Kafka connectivity, lag indicators/errors |
| `POST` | `/api/v1/analytics/refresh` | Re-ingest notes and rebuild marts |
| `GET` | `/api/v1/analytics/exports/daily-kpis.csv` | Stable Power BI import contract |

The same business routes are available through the API gateway at
`http://localhost:8080/api/v1/analytics/...` and nginx at `http://localhost/api/v1/analytics/...`.

## Run with PayNexus

From the repository root:

```bash
docker compose --env-file .env up --build -d
docker compose ps
curl http://localhost:8087/health
```

The shared `db-init` Compose job creates `paynexus_analytics` even when an existing Postgres volume means
the normal first-start scripts have already run.

Obtain an operator token:

```bash
TOKEN=$(curl --fail --silent \
  -X POST http://localhost:8088/realms/paynexus/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=paynexus-cli \
  --data-urlencode username=admin@paynexus.local \
  --data-urlencode 'password=Admin123!' | jq -r .access_token)

curl http://localhost/api/v1/analytics/kpis \
  -H "Authorization: Bearer $TOKEN"
```

On Windows PowerShell, use `curl.exe` and obtain the token with `ConvertFrom-Json`, as documented in the
root README. Create a few payments first and wait up to 30 seconds for mart refresh.

## Local Python development

Python 3.12 is required.

```bash
cd services/analytics-service
python -m venv .venv
source .venv/bin/activate              # Windows: .venv\Scripts\Activate.ps1
pip install -e ".[dev]"

export DATABASE_URL=postgresql+asyncpg://paynexus:<password>@localhost:5432/paynexus_analytics
export KAFKA_BROKERS=localhost:29092
export KEYCLOAK_ISSUER_URI=http://localhost:8088/realms/paynexus
export KEYCLOAK_JWK_SET_URI=http://localhost:8088/realms/paynexus/protocol/openid-connect/certs
uvicorn app.main:app --reload --port 8087
```

Use host Kafka port `29092`; port `9092` advertises the Docker-network hostname.

## Automated tests

```bash
ruff check app tests
pytest --cov=app --cov-report=term-missing --cov-fail-under=75
```

The suite covers:

- duplicate Kafka replay
- payment/fraud/ledger projection
- markdown idempotency
- minor-unit KPI and risk-rule pandas transforms
- mart replacement
- API contracts and CSV export
- ledger-integrity detection
- Keycloak role enforcement
- pipeline defaults

GitHub Actions runs this as a separate Python 3.12 job alongside Maven and frontend jobs. The analytics
Docker image is also included in the image build and Trivy scan matrix.

## Power BI

See `powerbi/README.md`. The recommended import source is
`/api/v1/analytics/exports/daily-kpis.csv`; direct PostgreSQL access to mart tables is also documented.
Grafana remains for technical SLIs, while Power BI is for business volume, conversion, and risk reporting.

## Recruiter demonstration

1. Create low-risk and review payments in the PayNexus UI.
2. Show the source events in `stg_raw_events`.
3. Call `/kpis`, `/risk-summary`, and `/ledger-integrity`.
4. Replay an event and show counts remain unchanged.
5. Add a markdown analyst note and call `/refresh`.
6. Open the Power BI model or import the CSV endpoint.
7. Open GitHub Actions and show Maven, npm/Playwright, pytest/coverage, image scan, and Compose smoke jobs.

This gives a concrete interview narrative: Java owns safe transaction processing; Python turns
at-least-once event streams and human notes into governed analytics without coupling reporting to OLTP.
