# PayNexus — Cloud-Native Payment Processing & Fraud Detection Platform

Java 21 · Spring Boot 3 · React · Keycloak · Kafka · PostgreSQL · Redis · Docker · Kubernetes · Terraform · Prometheus · Grafana · OpenTelemetry · Resilience4j

PayNexus is a portfolio-grade **wallet and payment orchestration platform**: the kind of system that sits behind a fintech product, not a tutorial CRUD app. A member authorizes a payment, funds are **reserved** in a ledgered wallet, a fraud engine scores the attempt, and money is **captured only after an approve decision**. High-risk or degraded fraud paths hold the reservation for a human analyst instead of silently charging the customer.

The dark, editorial web console is an original React implementation inspired by the clarity of premium consumer-finance products. It does not copy any vendor’s assets, source, or branding.

This repository is a **learning and interview project**. It never handles card numbers (PAN), CVV, bank credentials, or acquirer messages, and it is **not PCI DSS compliant**. Do not process real customer funds with this code.

---

## The problem it solves

Consumer payment products fail in boring, expensive ways:

| Failure | What happens without a real design | What PayNexus demonstrates |
| --- | --- | --- |
| Double tap / retry | The customer is charged twice | `Idempotency-Key` scoped per user, Redis lock, and a unique database constraint |
| Check-then-debit races | Two payments spend the same balance | Wallet ledger + optimistic locking + reserve/capture/release phases |
| Fraud outage | The platform either goes down or auto-approves | Circuit breaker → `REVIEW` → payment stays `PENDING_REVIEW` with funds reserved |
| Dual write (DB + Kafka) | Events vanish after a commit, or state rolls back after a publish | Transactional outbox in the same DB transaction as business writes |
| “UPDATE balance” accounting | No audit trail, no replay safety | Append-only ledger entries per payment phase |
| Ops at 2 a.m. | Logs without traces, no SLIs | Prometheus, Grafana, correlation IDs, OpenTelemetry → Jaeger |

A resume line that says “e-commerce app in Spring Boot” does not start that conversation. PayNexus does.

---

## What you can do in the product

- Sign in with **Keycloak** (Authorization Code + PKCE for the browser; client credentials for service-to-service calls).
- Open a wallet automatically when a user is provisioned (demo seed credit: **10,000,000 minor units**, e.g. ₹1,00,000.00).
- Create payments in **integer minor units** (`1499` = $14.99 / ₹14.99 depending on currency).
- Watch reserve → fraud → capture (or hold for review).
- As an admin, approve or reject the review queue, inspect risk explanations, and view reconciliation mismatches.
- See notifications and a searchable transaction history (eventually consistent read model).
- Inspect system health, metrics, and distributed traces.

---

## Architecture

```text
Browser (React + Keycloak PKCE)
  └── Nginx web (:80)  /api/* → API Gateway (:8080)
                              ├── User service          :8081
                              ├── Payment service       :8082  → Wallet, Fraud
                              ├── Wallet service        :8083
                              ├── Fraud service         :8084
                              ├── Notification service  :8085
                              └── Transaction service   :8086

Keycloak            :8088  (container :8080)
PostgreSQL          :5432  (one database per service)
Redis               :6379  (locks, rate limits, fraud velocity)
Kafka (KRaft)       :9092  (outbox events and read models)
Prometheus          :9090
Grafana             :3000
Jaeger              :16686 (OTLP :4317 / :4318)
```

There are **seven backend runtimes on purpose**. Each owns a bounded context. Fifteen microservices for a portfolio piece is theatre.

Internal Java packages and Maven coordinates still use `com.payflow` / `payflow-*` so module IDs stay stable. Product name, UI, realm, docs, Kubernetes namespace, and demo identities are **PayNexus**.

---

## Services — what each one does

### 1. Web console (`web/`, port 80)

React 18 + TypeScript + Vite. Members get overview, pay, wallet, transactions, notifications, and profile. Operators get fraud review, reconciliation, and system status.

Nginx serves the SPA and reverse-proxies `/api/` to the gateway so the browser does not talk to seven origins. Set `VITE_DEMO_MODE=true` only when you want in-browser mock data without the backend.

### 2. API gateway (`services/api-gateway`, port 8080)

Spring Cloud Gateway is the only public API surface.

- Validates Keycloak JWTs (issuer vs JWKS split — see below)
- Routes `/api/v1/users|payments|wallets|fraud|notifications|transactions/**`
- Redis rate limiting
- Correlation ID filter
- Authenticated `GET /api/v1/system/status` (aggregates downstream health)
- CORS for local Vite/nginx and the sample `https://web.paynexus.example` origin

### 3. User service (`services/user-service`, port 8081)

Identity is **not** a homemade password database. Keycloak issues tokens. This service:

- Upserts the local user on `GET /api/v1/users/me` from the token `sub`, email, name, country, and realm roles
- Stores KYC status
- Writes `user.registered` through a transactional outbox so the wallet can open asynchronously

Roles: `USER`, `ADMIN`, `SERVICE`.

### 4. Payment service (`services/payment-service`, port 8082) — the money path

This is the service you should be ready to talk about for twenty minutes.

```text
RESERVED
  ├── fraud APPROVE → CAPTURING → COMPLETED
  ├── fraud REVIEW or fraud outage → PENDING_REVIEW
  │     ├── admin approve → CAPTURING → COMPLETED
  │     └── admin reject  → release → REJECTED
  └── wallet/processing error → best-effort release → FAILED
```

Implemented:

- Integer `amountMinor` only (no decimal `amount` field)
- Idempotency per `(userId, Idempotency-Key)`
- Reserve funds **before** fraud; capture **only** on approve
- Admin review queue and approve/reject APIs
- Transactional outbox for payment state events
- Scheduled **reconciliation** against wallet ledger evidence, with conservative automatic repair
- Resilience4j retry + circuit breaker toward fraud and wallet (wallet writes are not blindly retried into double-spend)

### 5. Wallet service (`services/wallet-service`, port 8083)

A `balance` column that you `UPDATE` in place is not accounting. PayNexus uses an **append-only ledger** with phases:

| Phase | Meaning |
| --- | --- |
| `CREDIT` | Opening seed or inbound funds |
| `RESERVE` | Available ↓, reserved ↑ (authorization) |
| `CAPTURE` | Reserved ↓ (settlement of the hold) |
| `RELEASE` | Reserved ↓, available ↑ (void / reject / fail) |

Optimistic locking (`@Version`) plus unique phase-per-payment behavior makes retries safe. Concurrent debits: one commit wins; the loser retries against the new version and fails if funds are gone.

### 6. Fraud service (`services/fraud-service`, port 8084)

Deterministic rules plus an explanation layer (not a regulated ML model):

- High amount thresholds
- Redis velocity windows
- Country / merchant signals

Decisions: `APPROVE` / `REVIEW` / `REJECT`.

Also:

- `GET /api/v1/fraud/transactions/{paymentId}/risk-analysis` — score plus analyst English (template by default; OpenAI if `OPENAI_API_KEY` is set)
- `POST /api/v1/fraud/policies/ask` — RAG-lite over markdown policies under `services/fraud-service/src/main/resources/policies/`

When fraud is **down**, payment’s circuit breaker fallback returns `REVIEW`. Funds stay reserved. The platform does **not** auto-capture.

### 7. Notification service (`services/notification-service`, port 8085)

Consumes notification events, retries, and dead-letters poison messages (`notification.send.DLT`). Delivery is logged in this repo (swap SES/Twilio later without changing the contract). `GET /api/v1/notifications/me` is the member inbox.

### 8. Transaction service (`services/transaction-service`, port 8086)

CQRS-lite **read model**. Payment remains the write model. This service projects events into a searchable, paginated store (`status`, merchant, time). History can lag the synchronous payment API by a short interval — that is expected and is an interview talking point.

### Shared libraries

- `libs/common` — JWT conversion, `PayflowPrincipal`, errors, outbound HTTP timeouts, correlation
- `libs/events` — versioned Kafka payloads and topic names

---

## How to run the project

### Prerequisites

| Tool | Why |
| --- | --- |
| Docker Desktop (Compose v2) | One-command full stack |
| 8 GB+ RAM for Docker | Keycloak + 7 JVMs + Kafka + Postgres |
| JDK 21 + Maven 3.9+ | Optional host-side backend work |
| Node.js 22 + npm | Optional host-side UI work |
| `curl` and `jq` | Smoke scripts and HTTP examples |

### 1. Get the source

```bash
cd "path/to/payflow"   # repository folder name may still be payflow
```

### 2. Create a local env file

```bash
cp .env.example .env
```

`.env.example` already contains **development placeholders**. Change them if you share the machine. Required keys:

- `POSTGRES_PASSWORD`
- `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_PAYMENT_CLIENT_SECRET` (letters, digits, `.`, `_`, `~`, `-` only — used to render the realm import)
- `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`

Leave `VITE_DEMO_MODE=false` so the UI talks to real APIs.

### 3. Start everything

```bash
docker compose --env-file .env up --build --wait -d
```

First start downloads images and compiles services; **5–15 minutes** is normal. Keycloak imports realm `paynexus` after substituting the payment client secret.

Shortcut if you use the Makefile:

```bash
make up
make smoke
```

`make smoke` checks:

- `http://localhost:8080/actuator/health`
- `http://localhost/` (web)
- `http://localhost:8088/realms/paynexus/.well-known/openid-configuration`

### 4. Open the product

| URL | What |
| --- | --- |
| http://localhost | PayNexus web console |
| http://localhost:8080 | API gateway |
| http://localhost:8088 | Keycloak admin & OIDC |
| http://localhost:3000 | Grafana |
| http://localhost:9090 | Prometheus |
| http://localhost:16686 | Jaeger |

### 5. Sign in

| Role | Username | Password |
| --- | --- | --- |
| Member | `demo@paynexus.local` | `Demo123!` |
| Operator | `admin@paynexus.local` | `Admin123!` |

The browser uses public client `paynexus-web` (PKCE, no client secret). Admin screens require the `ADMIN` realm role (the UI compares roles case-insensitively).

On first API use, `GET /users/me` provisions the user. The wallet listener opens a ledgered wallet shortly after. If a payment returns `WALLET_NOT_FOUND`, wait a few seconds and retry.

### 6. Create a payment from the UI

1. Open **Send payment**.
2. Enter an amount (the form converts to minor units) and a merchant.
3. Submit. Keep the generated idempotency key if you want to prove replay later.

Low-risk payments complete; higher-risk or degraded fraud payments appear in **Fraud reviews** for `admin@paynexus.local`.

### 7. Call the API from a terminal

Password grant is enabled **only** on public client `paynexus-cli` for local demos. Do not use this flow in production.

```bash
TOKEN=$(curl --fail --silent \
  -X POST http://localhost:8088/realms/paynexus/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=paynexus-cli \
  --data-urlencode username=demo@paynexus.local \
  --data-urlencode 'password=Demo123!' | jq -r .access_token)

curl --fail-with-body http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"

curl --fail-with-body http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: readme-demo-001' \
  -d '{"amountMinor":150000,"currency":"INR","merchantId":"AMZN-IN","country":"IN"}'
```

Repeat the payment `curl` with the **same** idempotency key. You should get the same `paymentId`.

Walkthroughs: `docs/smoke.http`, `docs/resilience.http`.

### 8. Resilience demos (optional)

```bash
export KEYCLOAK_USERNAME=demo@paynexus.local
export KEYCLOAK_PASSWORD='Demo123!'
chmod +x docs/demos/*.sh

docs/demos/idempotent-replay.sh
docs/demos/fraud-outage-recovery.sh   # expects PENDING_REVIEW, funds held
docs/demos/wallet-timeout-fail-fast.sh
docs/demos/notification-dlt.sh
docs/demos/traced-payment.sh
```

See `docs/demos/README.md`.

### 9. Stop

```bash
docker compose --env-file .env down --volumes   # removes local DB/Kafka data
```

### Host-side development (optional)

Infrastructure only:

```bash
docker compose --env-file .env up -d postgres redis kafka keycloak jaeger
```

Then from the repo root:

```bash
mvn -pl services/api-gateway -am spring-boot:run
# similarly for each service, with matching env vars from docker-compose.yml
```

Frontend with Vite proxy to the gateway:

```bash
cd web
cp .env.example .env.local
# set VITE_DEMO_MODE=false and VITE_KEYCLOAK_URL=http://localhost:8088
npm ci
npm run dev
```

UI-only (no backend): `VITE_DEMO_MODE=true npm run dev`.

### Tests

```bash
mvn clean verify          # unit tests always; Testcontainers ITs need Docker
cd web && npm ci && npm run lint && npm test && npm run build
npx playwright install chromium && npm run test:e2e
```

### Issuer vs JWKS (common footgun)

Browser tokens have issuer `http://localhost:8088/realms/paynexus`. Containers cannot fetch JWKS through that host URL reliably, so resource servers use:

- `KEYCLOAK_ISSUER_URI=http://localhost:8088/realms/paynexus` (must match `iss`)
- `KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/paynexus/protocol/openid-connect/certs` (internal DNS)

Do not point the issuer at `http://keycloak:8080` or browser logins will fail JWT validation.

---

## Observability

Every service exposes `/actuator/health` and `/actuator/prometheus`. Grafana dashboard **PayNexus Business & Resilience** charts payment outcomes, review volume, reconciliation, wallet phases, HTTP latency, and circuit-breaker health. Metric names still use the `payflow_*` prefix in Prometheus (Micrometer meters in code).

Logs include trace, span, correlation, payment, and user fields when present. Local trace sampling is `1.0`; lower `TRACING_SAMPLING_PROBABILITY` outside demos.

---

## Kubernetes and AWS samples

```bash
# copy k8s/secrets.example.yaml → secrets.local.yaml, fill values, apply
kubectl apply -f path/to/secrets.local.yaml
kubectl create secret tls paynexus-tls --cert=cert.pem --key=key.pem -n paynexus
kubectl apply -k k8s
```

Namespace: `paynexus`. Payment service has an HPA (2–8 replicas). Ingress hosts in the sample are `web.paynexus.example` and `auth.paynexus.example`. Replace domains, TLS, and image registries before any real cluster.

`terraform/` is a **cost-aware skeleton** (VPC, EKS, RDS, ElastiCache, S3, Secrets Manager). Do not `terraform apply` unless you intend to pay for those resources. Worker nodes, NAT, MSK, IAM hardening, and DNS remain platform work.

---

## Suggested resume title and bullets

**PayNexus — Cloud-Native Payment Processing & Fraud Detection Platform**  
Java 21 · Spring Boot · Microservices · Kafka · PostgreSQL · Redis · Keycloak · Kubernetes · Terraform

- Designed an event-driven payment platform with reserve/capture/release wallet accounting, integer minor units, and per-user idempotency so retries cannot double-charge.
- Implemented Keycloak OAuth2 (PKCE for the SPA, client credentials for service calls) and role-based admin review instead of homemade password JWTs.
- Used a transactional outbox, Kafka read models, and conservative ledger reconciliation to close dual-write and drift gaps.
- Applied Resilience4j, Prometheus/Grafana, and OpenTelemetry traces, with executable demos for fraud outage (hold-for-review) and wallet timeouts.
- Containerized seven services plus a React console; added Compose, Kustomize/HPA, GitHub Actions, and a Terraform AWS skeleton.

---

## Future enhancements

These are realistic next steps, not unfinished homework disguised as vision:

1. **Real acquiring / rails** — ISO 8583 or a sandbox PSP (Stripe/Adyen test mode) behind an anti-corruption layer; still keep PAN out of this repo.
2. **Idempotent PSP callbacks** — webhook signature verification, at-least-once capture/refund notifications, and state machines for `REFUNDING` / `CHARGEBACK`.
3. **Stronger fraud** — feature store, graph features (device, BIN, merchant), model scoring service, and shadow-mode vs enforce-mode.
4. **pgvector / OpenSearch RAG** — replace markdown chunking with embeddings and citations for policy Q&A.
5. **Exactly-once-ish money** — Kafka transactions or outbox + inbox pattern on every consumer; currently at-least-once with idempotent handlers.
6. **Multi-currency exponents** — ISO 4217 minor-unit tables (JPY=0, KWD=3) instead of assuming two decimals in the UI.
7. **mTLS and service mesh** — drop bearer tokens for east-west traffic; rotate signing keys and use short-lived SVIDs.
8. **Production Keycloak** — no direct access grants, required actions, brute-force protection, theme, and external IdP (Google/enterprise SSO).
9. **HA data plane** — managed Postgres, Redis, and Kafka; backups, PITR, pod disruption budgets, network policies, image digests.
10. **Product** — scheduled payments, mandates, beneficiary book, dispute UI, merchant settlement reports, and accessibility audit on the console.
11. **Load and chaos** — k6/Gatling against payment HPA; Chaos Mesh experiments that the demo scripts already hint at.
12. **Compliance theater → real controls** — threat model, secret scanning gates, SBOM, data retention, and an explicit PCI scope diagram that keeps this app out of CDE.

---

## Interview talking points

- Why minor units beat `double` for money, and where currency exponents belong.
- Why issuer validation and JWKS retrieval use different URLs.
- PKCE public client vs confidential client-credentials.
- Idempotency at orchestration **and** ledger phase boundaries.
- Why outbox still needs idempotent consumers.
- Why reserve/review/capture is safer than debit-then-score.
- Why reconciliation refuses to “fix” ambiguous ledger evidence.
- What is synchronous today vs eventually consistent (transactions, notifications, wallet open).
- What must change before production: regulated rails, PCI scope, managed HA, key rotation, threat modeling.

---

## Repository map

```text
libs/             shared security, errors, HTTP, event contracts
services/         gateway + six Spring Boot services
web/              React/Vite app and Nginx proxy
keycloak/         realm export (paynexus)
docker/           JVM image, Keycloak entrypoint, Postgres init
k8s/              Kustomize runtime and observability
terraform/        illustrative AWS infrastructure
observability/    Prometheus scrape + Grafana dashboard
docs/             HTTP clients and executable resilience demos
.github/workflows CI, image build/scan, optional deploy
```

---

## License and limits

Educational portfolio project. Not a licensed payment institution, not PCI DSS / SOC 2 assessed, not a production fraud engine. Local HTTP, demo passwords, and development Keycloak are unsafe for shared or public environments. Keep secrets out of git (`.env` is gitignored).
