# Budget Web App — Tech Stack & Implementation Plan

## Context

You want a personal budgeting web app: upload CSV bank statements, see your
spending visualised in graphs, track current savings, and get a recommendation
for how much to save each month to hit a savings goal. Single user, runs on
localhost first, then containerised with Docker for self-hosting later. React
frontend, Java backend, Gradle build.

The current repo is a bare IntelliJ project with one `src/Main.java`. Effectively
greenfield — we're picking the stack from scratch.

---

## Recommended Tech Stack

### Backend (Java + Gradle)

| Concern | Choice | Why |
|---|---|---|
| Language | **Java 21 LTS** (Microsoft Build of OpenJDK, installed via winget) | Spring Boot officially targets LTS releases. Java 26 was the previously-installed JDK but lacks ecosystem support — installed Java 21 alongside it. |
| Framework | **Spring Boot 4.0.x** (Initializr default) | Industry standard, batteries-included REST + JPA + validation. Note: Boot 4 uses the new modular starter naming (`spring-boot-starter-webmvc`, `spring-boot-h2console`) — different from older 3.x examples online. |
| Build | **Gradle (Kotlin DSL)** | Per your preference. `build.gradle.kts` with Spring Boot + Dependency Management plugins. |
| Persistence | **H2 in file mode** for Phase 1, **PostgreSQL** when Dockerised | H2 file-mode = zero setup, single `.db` file, ideal for local-only. Postgres in Phase 2 swaps in via a Spring profile and a `docker-compose` service. |
| ORM | **Spring Data JPA + Hibernate** | Repository pattern, query derivation. Standard fare. |
| CSV parsing | **Apache Commons CSV** | Robust, handles quoting/escaping. Aligns with your Apache Commons preference. |
| Validation | **Jakarta Bean Validation** (Spring Boot starter) | `@Valid`, `@NotNull`, `@Positive` on DTOs. |
| Testing | **JUnit 5 + AssertJ + Mockito** | Matches your existing `java-testing` skill. |

### Frontend (React)

| Concern | Choice | Why |
|---|---|---|
| Bundler | **Vite** | Fast dev server, modern defaults, replaces CRA which is deprecated. |
| Language | **TypeScript** | Catches API contract drift early; well worth the small overhead. |
| Charts | **Recharts** | Idiomatic React, declarative, covers all the graphs you need (pie, line, bar, area). Smaller learning curve than ECharts or D3. |
| HTTP / state | **TanStack Query** + `fetch` | Caching, loading/error states, refetching — saves writing it yourself. |
| Styling | **Tailwind CSS** | Fastest path to a clean UI without writing CSS files. Skip if you'd rather use plain CSS modules. |
| File upload | **react-dropzone** | Drag-and-drop CSV upload with one component. |
| Forms | **React Hook Form** + **Zod** | Lightweight, typed validation that mirrors backend rules. |

### Containerisation (Phase 2)

| Concern | Choice |
|---|---|
| Backend image | Multi-stage `Dockerfile`: Gradle build → JRE runtime (eclipse-temurin) |
| Frontend image | Multi-stage: `node` build → `nginx:alpine` serving `dist/` |
| Orchestration | `docker-compose.yml` with `backend`, `frontend`, `postgres` services |
| DB volume | Named volume for Postgres data persistence |

---

## Project Structure

```
adeleTest/
├── backend/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/budget/
│       │   │   ├── BudgetApplication.java
│       │   │   ├── statement/          # CSV upload + parsing
│       │   │   ├── transaction/        # Transaction entity, repo, service
│       │   │   ├── category/           # Categorization rules
│       │   │   ├── insight/            # Aggregations for charts
│       │   │   └── savings/            # Goal calculator + savings snapshots
│       │   └── resources/
│       │       ├── application.yml
│       │       └── application-docker.yml
│       └── test/java/com/budget/...
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── api/                        # Typed API client
│       ├── components/
│       ├── pages/
│       │   ├── Upload.tsx
│       │   ├── Dashboard.tsx
│       │   └── SavingsGoal.tsx
│       └── App.tsx
├── docker-compose.yml                  # Added in Phase 2
└── README.md
```

The existing `src/Main.java` and IntelliJ config should be removed or moved
aside — the backend module will own the new Java source root.

---

## Domain Model

- **Transaction** — `id`, `date`, `description`, `amount` (signed: negative = debit), `categoryId`, `sourceStatementId`
- **Statement** — `id`, `uploadedAt`, `bankName`, `originalFilename`, `rowCount`
- **Category** — `id`, `name`, `keywords[]` (for rule-based auto-categorization), `colour`
- **SavingsSnapshot** — `id`, `recordedAt`, `amount`, `accountName`
- **SavingsGoal** — `id`, `name`, `targetAmount`, `targetDate`, `currentAmount`

---

## Feature Breakdown

### 1. Statement Import — CSV upload **or** direct bank API

Two ways in, sharing the same categorise-and-store pipeline:

- **Bank/brokerage APIs** (see `CONNECTIONS.md`) — link Monzo and Trading 212 via
  their free native APIs, and Santander/Amex through the Plaid Open Banking
  aggregator. Implemented under `com.budget.connection` behind a pluggable
  `BankConnector` interface; synced rows are de-duplicated by provider transaction id.
- **CSV upload** (below) — kept as a fallback and for providers without a connection.

#### CSV Upload & Parsing
- Endpoint: `POST /api/statements` (multipart/form-data)
- Frontend: drag-and-drop zone → POST file → show row preview before commit
- Backend: parse with Apache Commons CSV, autodetect column mapping (date, description, amount) by header name. Allow user to confirm/remap if ambiguous.
- Persist `Statement` + N `Transaction` rows in one transaction.

### 2. Auto-Categorization
- Seed default categories: Groceries, Transport, Eating Out, Rent/Mortgage, Utilities, Entertainment, Income, Transfers, Other.
- Rule-based matching on `description` against `Category.keywords` (case-insensitive contains).
- Frontend: edit category per transaction → updates a keyword rule retroactively (optional).

### 3. Dashboard Charts (Recharts)
- **Donut**: spending by category (current month)
- **Line**: monthly spending trend (last 12 months)
- **Bar**: income vs. expenses per month
- **Area**: cumulative savings over time (from `SavingsSnapshot` history)
- **Progress bar**: savings goal — current vs. target with on-track indicator

### 4. Savings Goal Recommendation
- Inputs: current savings, goal amount, goal date.
- Backend computes:
  - `monthsRemaining = monthsBetween(today, goalDate)`
  - `gap = goalAmount - currentSavings`
  - `monthlyNeeded = gap / monthsRemaining`
  - `avgMonthlyDisposable = avg(income - expenses) over last 3 months`
  - Returns: `monthlyNeeded`, `onTrack` (boolean), `suggestedCategoriesToCut` (top 2 discretionary categories by spend if `monthlyNeeded > avgMonthlyDisposable`)
- Endpoint: `POST /api/savings/recommend`

### 5. Current Savings Input
- Simple form to add a `SavingsSnapshot` (amount + optional account label).
- History list with edit/delete.
- Latest snapshot drives goal-progress UI.

---

## Phase 1 — Localhost

1. Initialize Gradle Spring Boot project under `backend/` (use `start.spring.io` settings: Web, JPA, Validation, H2).
2. Add Apache Commons CSV dependency.
3. Implement entities + repositories + a minimal `POST /api/statements` + `GET /api/transactions`.
4. Configure CORS to allow `http://localhost:5173`.
5. Run backend: `./gradlew bootRun` → serves on `:8080`.
6. Initialize Vite React-TS project under `frontend/` (`npm create vite@latest frontend -- --template react-ts`).
7. Add Tailwind, Recharts, react-dropzone, React Hook Form, TanStack Query.
8. Configure Vite dev proxy: `/api` → `http://localhost:8080`.
9. Build Upload → Dashboard → Savings pages.
10. Run frontend: `npm run dev` → serves on `:5173`.

### Critical files to create
- `backend/build.gradle.kts` — dependency list + Spring Boot plugin
- `backend/src/main/resources/application.yml` — H2 file-mode DB URL, JPA config
- `backend/src/main/java/com/budget/BudgetApplication.java` — entry point
- `backend/src/main/java/com/budget/statement/StatementController.java` — upload endpoint
- `backend/src/main/java/com/budget/statement/CsvParser.java` — column-mapping logic using `org.apache.commons.csv.CSVParser`
- `backend/src/main/java/com/budget/savings/SavingsRecommender.java` — gap/monthly calc
- `frontend/vite.config.ts` — proxy config
- `frontend/src/api/client.ts` — typed fetch wrapper

---

## Phase 2 — Docker

1. Add `backend/Dockerfile` (multi-stage: gradle:jdk21 → eclipse-temurin:21-jre).
2. Add `frontend/Dockerfile` (multi-stage: node:lts → nginx:alpine).
3. Add `application-docker.yml` with Postgres datasource and `SPRING_PROFILES_ACTIVE=docker`.
4. Add `docker-compose.yml` with `backend`, `frontend`, `postgres` (named volume `pgdata`).
5. Swap H2 → Postgres dependency (`org.postgresql:postgresql`); Hibernate handles dialect via Spring Boot auto-config.
6. Verify with `docker compose up --build`.

---

## Verification

### Phase 1 end-to-end test
1. `./gradlew bootRun` in `backend/` — confirm `Started BudgetApplication` log.
2. `npm run dev` in `frontend/` — open `http://localhost:5173`.
3. Drop a sample CSV (e.g., last month's bank export) onto Upload page → confirm row count and category preview.
4. Navigate to Dashboard → all charts render with parsed data.
5. Enter current savings on Savings page → set a goal → confirm `monthlyNeeded` and on-track indicator display.
6. Backend tests: `./gradlew test` — `CsvParserTest`, `SavingsRecommenderTest`, controller slice tests with `@WebMvcTest`.

### Phase 2 end-to-end test
1. `docker compose up --build` — all three services healthy.
2. Open `http://localhost` (nginx-served frontend) — same UI works.
3. `docker compose down && docker compose up` — Postgres volume persists data.

---

## Open Considerations

- **Java 21 vs 26**: I'm recommending 21 LTS for ecosystem stability. If you want to stay on 26 for the language features, expect occasional friction with Spring/Hibernate annotation processors — manageable, just slower to debug.
- **CSV column mapping**: bank CSVs vary wildly (Barclays, Monzo, HSBC, Starling all differ). Start with a manual mapper UI; auto-detection is a v2.
- **No auth**: localhost-only means no login. When you self-host, add a single-password gate via Spring Security's `formLogin` — minimal effort.
- **Currency**: assume single currency (GBP based on your email TLD). Add a `currency` column on `Transaction` only if multi-currency becomes relevant.
