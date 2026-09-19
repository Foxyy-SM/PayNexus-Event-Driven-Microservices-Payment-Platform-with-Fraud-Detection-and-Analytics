# PayNexus Web

A premium, accessible payments console built with React, TypeScript, Vite, TanStack Query, React Router, and Keycloak Authorization Code + PKCE.

## Run locally

```bash
npm install
npm run dev
```

The app talks to live PayNexus APIs unless `VITE_DEMO_MODE=true`. For UI-only work, copy `.env.example` to `.env.local` and keep `VITE_DEMO_MODE=true`. Set it to `false` (and point Keycloak at `http://localhost:8088`) to use the Compose stack.

## Authentication

The production authentication path uses `keycloak-js` with:

- Authorization Code flow with PKCE (`S256`)
- no implicit-flow tokens
- automatic token refresh
- realm-role based route guards

Configure the Keycloak client with valid redirect URIs for the deployed origin and enable Standard Flow. Admin screens require the `admin` realm role.

## API contract

The typed client in `src/api.ts` targets:

- `/api/v1/users/me`
- `/api/v1/wallets/me` and `/api/v1/wallets/me/ledger`
- `/api/v1/payments` and `/api/v1/payments/{id}`
- `/api/v1/payments/admin/review-queue` and `/api/v1/payments/{id}/review/{approve|reject}`
- `/api/v1/transactions`
- `/api/v1/fraud/transactions/{paymentId}/risk-analysis`
- `/api/v1/notifications/me`
- `/api/v1/payments/admin/reconciliation/mismatches`
- `/api/v1/system/status`

Money is represented as integer minor units. Payment submissions include a client-generated UUID idempotency key.

## Quality checks

```bash
npm test
npm run build
npm run test:e2e
```

Playwright browser binaries must be installed once with `npx playwright install`.

## Container

```bash
docker build \
  --build-arg VITE_DEMO_MODE=false \
  --build-arg VITE_KEYCLOAK_URL=https://identity.example.com \
  -t paynexus-web .
docker run -p 8088:80 paynexus-web
```

Nginx serves the SPA, supports client-side routes, exposes `/healthz`, caches fingerprinted assets, and proxies `/api/` to `api-gateway:8080`.
