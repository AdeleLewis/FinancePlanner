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

### The Plaid (Santander/Amex) flow

End to end and wired up: `/api/connections/plaid/link-token` issues a Link token, the
`PlaidConnect` component in `frontend/src/pages/Connections.tsx` opens Plaid Link via
`usePlaidLink` (`react-plaid-link`), and the returned `public_token` is POSTed to
`/api/connections/plaid/exchange`, which swaps it for a stored access token.

## Security note

Secret columns on `BankConnection` (`access_token`, `refresh_token`, `api_key`) are
**encrypted at rest** with AES-256-GCM via a JPA `AttributeConverter`
(`com.budget.security.EncryptedStringConverter`). The key is derived from
`APP_ENCRYPTION_KEY` and never stored in the database, so a stolen DB file or backup
does not expose tokens. If the key is unset the app falls back to plaintext and logs a
warning — set it before exposing the app beyond localhost:

```bash
export APP_ENCRYPTION_KEY=$(openssl rand -base64 32)   # keep this stable; rotating it cannot decrypt old rows
```

Legacy rows written before encryption was enabled are read back transparently (they
lack the `enc:v1:` marker) and re-encrypted on the next write. This protects against
DB-file theft, not full host compromise (an attacker with the running process also has
the key) — step up to a KMS/HSM for that. The H2 web console is disabled for the same
reason. Also put the app behind the single-password gate noted in `PLAN.md`.

### Choosing a database

The default is an embedded H2 file DB — fine for single-user localhost. A `postgres`
Spring profile and a root `docker-compose.yml` are provided for when you want
durability, real concurrency, or to deploy off-box:

```bash
docker compose up -d db
cd backend && SPRING_PROFILES_ACTIVE=postgres APP_ENCRYPTION_KEY=... ./gradlew bootRun
```

Switching databases does **not** change the encryption story — secrets are encrypted by
the app before they reach either DB.
