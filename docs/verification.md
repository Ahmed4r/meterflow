# Verification record

## CV-readiness review — 2026-11-11

The pinned Maven 3.9.11 wrapper completed `spotless:apply verify`; a separate
`spotless:check` passed. All **32 tests passed**, with zero failures or skips:
16 PostgreSQL/Redis integration tests, five pricing tests, five provider transport
tests, two rate-limiter tests and four ArchUnit architecture rules.

New regressions cover timestamp precision on identical retries, ingestion racing
month closure, refresh-token reuse revocation committed despite HTTP 401, unrelated
invalid tokens, tier boundaries and model attempts to swap verified amounts.

JaCoCo measured **847/1,202 executable lines (70.5%)** overall and **41/42 (97.6%)**
in `Pricing`. Coverage describes exercised code, not proof of correctness. Reports
are generated under `backend/target/site/jacoco` and uploaded by the CI definition.
No arbitrary percentage gate was added to hide untested paths.

Frontend formatting and TypeScript checks passed. Type checking now regenerates
Next.js route types, avoiding stale generated paths or a dependency on a prior build.

The updated backend and frontend Docker images built successfully. All four Compose
services reported healthy, and the frontend login returned HTTP 200 on localhost:3001.
All **four Playwright tests passed** against these updated services: accessibility,
dashboard/filter/download/mobile workflows, simultaneous-tab refresh, and registration
through customer/plan/subscription/key/export/deletion. External AI was not called.
The README screenshot contains synthetic demo data. Remote GitHub CI remains unverified.

## Previous runtime baseline

2026-11-11: all four Docker Compose services started successfully and reported healthy.
Frontend is configured on localhost:3001; backend on localhost:8080. Repaired stale AI
provider tests to match the current `explain` contract and preserved Groq configuration.
All 21 backend tests passed (12 PostgreSQL/Redis integration, 4 pricing, 3 provider
transport, 2 rate-limiter tests). No tests were skipped.

All three Playwright suites also passed against the Docker-hosted frontend/backend:
login/dashboard accessibility in light and dark mode; seeded dashboard, navigation,
filters, downloads and mobile layout; and registration, deterministic analysis,
customer/plan/subscription workflows, key revocation, personal export and deletion.
External AI was not invoked: the analyst workflow uses a newly registered, opted-out
organization. Existing demo organization AI preferences were left unchanged.

Local environment: Windows, project-local Temurin Java 21, Maven 3.9.11, Node 20.19,
Docker Desktop with Linux containers. Synthetic data only.

Initial foundation verification passed pricing unit tests and six PostgreSQL/Redis
integration tests. Subsequent verification adds provider transport tests, AI tenant
authorization, subscription cancellation, concurrent billing, request validation and
ownership-safe deletion. See Maven's generated `backend/target/surefire-reports` for
actual counts and the latest run status; generated reports are not committed.

The frontend production build passed after restoring missing global CSS and replacing
the incorrect route with explicit pages. The browser workflow for registration,
customer/plan creation, assignment, cancellation, API keys, export and deletion passed.
The dashboard suite initially contained an order-dependent assertion on seeded invoices;
it was corrected to check the number of recent rows rather than a random UUID order.

Browser screenshots and traces are local under `frontend/test-results`. The in-app
browser was unavailable, so verification uses a separate Playwright Chromium instance.

No live AI-provider call, public deployment, real payment, certificate issuance or
legal/privacy certification was performed. GitHub Actions configuration must still
run on a pushed repository; local success is not a claim of remote CI success.
