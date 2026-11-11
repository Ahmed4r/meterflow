# Portfolio and interview guide

Present MeterFlow as an independent portfolio project. Its strongest evidence is the
transactional billing and tenant isolation, supported by tests against PostgreSQL.
Avoid claims about production customers, compliance, scale or commercial deployment.

## Five-minute demonstration

1. Start Docker Compose using the README. Sign in to the synthetic demo workspace;
   show usage, service filters and a completed invoice with its PDF.
2. Register a separate organization. Show its empty dashboard, create a customer
   and plan, assign a subscription and generate a tenant API key.
3. Follow `docs/api.md` to submit usage. Repeat the same idempotency key and payload:
   the original receipt is returned. Change the payload: the server returns 409.
4. Show the PostgreSQL integration tests for concurrent ingestion, concurrent month
   closure and cross-tenant requests. Explain the database constraints and row lock.
5. Show the analyst with AI disabled, then the privacy controls and personal export.
   Explain that optional AI only selects verified evidence and cannot change bills.

Use synthetic data throughout. Never put a generated API key in screenshots or commits.

## CV wording

**MeterFlow — Java / Spring Boot SaaS portfolio project**

- Built a multi-tenant usage billing application with Java 21, Spring Boot,
  PostgreSQL, Redis and a Next.js dashboard, packaged with Docker Compose.
- Implemented transactional, idempotent usage ingestion and monthly invoicing using
  database constraints, pessimistic locking and BigDecimal financial calculations.
- Tested tenant isolation, token revocation and concurrent billing with JUnit and
  Testcontainers; added browser workflows, architecture checks and CI coverage reports.

German alternative:

**MeterFlow — Eigenständiges Java-/Spring-Boot-Portfolioprojekt**

- Mandantenfähige Anwendung zur nutzungsbasierten Abrechnung mit Java 21, Spring Boot,
  PostgreSQL, Redis und Next.js entwickelt und mit Docker Compose bereitgestellt.
- Idempotente Ereignisverarbeitung und monatliche Rechnungsstellung mit Transaktionen,
  Datenbank-Constraints, pessimistischen Sperren und BigDecimal implementiert.
- Mandantentrennung, Token-Widerruf und konkurrierende Abrechnung mit JUnit und
  Testcontainers geprüft; Browser-Tests, Architekturprüfungen und CI ergänzt.

Choose two bullets if CV space is limited. Link the repository and verification record.
Describe your use of development assistance honestly when asked; be ready to explain
and modify the code yourself.

## Interview topics to prepare

| Question | Implementation and tradeoff |
| --- | --- |
| Why a modular monolith? | One transactional database and small deployment; modules organize responsibilities without distributed coordination. |
| What prevents double billing? | Unique invoice period, one transaction, subscription row lock shared with ingestion. |
| Why not only check for duplicates first? | Concurrent requests can both pass a read; the unique constraint is the final authority. |
| Why normalize timestamps? | PostgreSQL microsecond precision must match retry comparison; nanoseconds are truncated before persistence. |
| Why JPA and JDBC? | JPA handles subscription locking; targeted SQL handles aggregates and conflict-aware writes in the same transaction. |
| How is refresh theft handled? | Rotated-token digests identify reuse; revocation must commit despite returning 401. |
| What is the locking cost? | Ingestion serializes per subscription. No throughput claim is made; benchmark before changing this design. |
| Why discard model prose? | Valid numbers can still be attached to wrong periods; only server-owned evidence is displayed. |
| What is missing for commercial billing? | Taxes, payment collection, credit notes, proration, operational security review and legal retention policy. |

Before sharing: run CI on the actual GitHub repository, include a synthetic dashboard
screenshot and ensure setup instructions work on a clean machine. A configured workflow
is not evidence that remote CI passed. Do not invent a live demo URL or performance metrics.
