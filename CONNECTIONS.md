# Bank & Brokerage Connections

Instead of (or alongside) uploading CSVs, the app can pull transactions directly
from a provider's API. Synced rows flow through the same categorisation and
storage as CSV imports, and are de-duplicated on each sync by the provider's
native transaction id.

Open the **Connections** tab to link an account and hit **Sync now**.

## What each provider supports

| Provider | Auth | Server setup needed? | Notes |
|---|---|---|---|
| **Monzo** | OAuth2 (native API) | Yes — client id/secret | Free, your own account only. Register a **confidential** client to get a refresh token, otherwise access expires and you must reconnect. Transactions older than 90 days are only available for ~5 min after login (Monzo SCA rule); ongoing sync is unaffected. |
| **Trading 212** | API key (native API) | No | Free. Generate a key in the app (Settings → API) and paste it in. Beta API, Invest/ISA accounts only. Imports cash movements (deposits, withdrawals, dividends, fees), not card spend. |
| **Santander** | Plaid (aggregator) | Yes — Plaid client id/secret | No hobbyist API exists; reached through the Plaid Open Banking aggregator. |
| **American Express** | Plaid (aggregator) | Yes — Plaid client id/secret | Same as Santander. |

> Open Banking consent (Santander/Amex via Plaid) legally expires every **90 days**
> and must be re-authorised. That's regulation, not a limitation of this app.

## Configuration

Credentials are read from environment variables (see `backend/src/main/resources/application.yml`).
A connector with no credentials still appears in the UI but is shown as disabled.

```bash
# Monzo — https://developers.monzo.com (use a "confidential" client)
export MONZO_CLIENT_ID=...
export MONZO_CLIENT_SECRET=...
# Redirect URI must match the one registered with Monzo:
export MONZO_REDIRECT_URI=http://localhost:8080/api/connections/monzo/callback

# Plaid — https://dashboard.plaid.com (Santander + Amex)
export PLAID_CLIENT_ID=...
export PLAID_SECRET=...
export PLAID_ENV=sandbox          # sandbox | development | production

# Trading 212 — no server config; key is entered per-connection in the UI
export TRADING212_ENV=live         # live | demo
```

Trading 212 needs nothing here — each connection carries its own API key.

## Architecture

A small pluggable layer under `com.budget.connection`:

- **`BankConnector`** — the common interface; one implementation per provider
  (`MonzoConnector`, `Trading212Connector`, and `PlaidConnector` with
  `SantanderConnector` / `AmexConnector` subclasses).
- **`NormalizedTransaction`** — provider-agnostic row each connector emits.
- **`BankConnection`** (entity) — a linked account plus its tokens/keys and status.
- **`ConnectionService`** — fetch → de-duplicate by `external_id` → categorise →
  persist, reusing the existing `Categorizer` and `TransactionRepository`.
- **`ConnectionController`** — REST API (`/api/connections/**`) including the Monzo
  OAuth round-trip and Plaid Link token exchange.

Adding another bank = implement `BankConnector` and add a `BankProvider` enum value.

### Finishing the Plaid (Santander/Amex) flow

The backend is complete: `/api/connections/plaid/link-token` issues a Link token and
`/api/connections/plaid/exchange` swaps the Link `public_token` for a stored access
token. The only remaining piece is opening the Plaid Link modal in the browser, which
needs the Plaid Link SDK:

```bash
npm i @plaid/react-plaid-link   # in frontend/
```

Then call `usePlaidLink({ token: linkToken, onSuccess })` and POST the returned
`public_token` to `/api/connections/plaid/exchange`. See the `PlaidConnect`
component in `frontend/src/pages/Connections.tsx` for where to wire it in.

## Security note

This is a single-user, localhost-first app, so tokens/keys are stored in plaintext
in the H2 database. Before exposing it beyond localhost, encrypt the secret columns
on `BankConnection` at rest (e.g. a JPA `AttributeConverter` keyed from the
environment) and put the app behind the single-password gate noted in `PLAN.md`.
