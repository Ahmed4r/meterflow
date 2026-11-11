# Security and personal data

## Boundaries

Each protected organization operation checks a current membership. Resource queries
include tenant predicates. Composite database foreign keys prevent cross-tenant
customer/plan/subscription relationships. Tenant API keys carry no tenant input from
the caller and are stored only as SHA-256 hashes of high-entropy random values.

BCrypt uses cost 12. Password input is bounded to 72 UTF-8 bytes to avoid silent BCrypt
truncation during registration. JWT signing uses Nimbus via Spring Security; secrets
must contain at least 32 bytes. JWT lifetimes are 15 minutes. Every bearer request also
checks its session row so logout/deletion take effect immediately. Refresh tokens
rotate under a row lock. Reusing a previously rotated token revokes its session,
including the latest refresh token and outstanding access tokens. Only digests of
previous tokens are retained, cascading away with the session. Revocation commits
even when the refresh endpoint returns HTTP 401. Browser Web Locks serialize refresh
across tabs where supported; browsers without this API may require signing in again
after simultaneous refreshes. A lost refresh response also requires signing in again.

Browser access tokens stay in memory. Refresh tokens are HttpOnly, SameSite=Strict,
path-scoped cookies. Cookie-authenticated refresh/logout requests require an exact
Origin match. Other writes require explicit bearer headers. CORS permits the configured
origin only. No wildcard credentialed CORS or token storage in localStorage is used.

Rate limits use an atomic Redis INCR/EXPIRE script. Redis failure denies new ingestion,
authentication and analyst calls instead of silently removing limits. Current windows
are fixed 60-second intervals; they are not a substitute for edge DDoS protection.

## Logging and secrets

Application error logs record exception classes only, not exception messages or SQL
parameters. Request/response bodies and authorization headers are not logged. AI SDK
exceptions are not logged. ECS structured logs contain service metadata and lifecycle
events. Do not enable HTTP wire logging, Hibernate parameter tracing or SDK debug
logging with real data. Audit reverse-proxy and hosting logs separately.

`.env` is ignored. Environment variables configure credentials; use an actual secret
manager in deployment. Database and Redis ports bind to loopback in local Compose.
Local HTTP and unencrypted Docker-network connections are development settings, not
an encryption guarantee. See operations guidance for TLS and restricted networks.
Encryption at rest depends on the host/managed database and is not supplied by this app.

## Export, deletion and retention

Export returns the user's profile, memberships and non-secret session metadata.
Deletion requires password confirmation and removes the identity, password hash,
memberships and sessions. If a user is the only owner of a shared organization, deletion
is blocked until they assign another owner. A sole-member organization's name and
customer labels are anonymized, subscriptions cancelled and API keys revoked.

Organization billing records and invoice snapshots remain. Customer reference/name
fields can contain personal data, so operators must apply the relevant business/legal
retention policy and review any free-form plan, service or key labels. This prototype
does not certify that every user-supplied string is anonymous. Billing retention is
an explicit exception, not a claim that indefinite retention is legally justified.

Raw usage older than `RETENTION_DAYS` (default 400, minimum 90) is deleted in bounded
daily batches only after an invoice snapshot exists. Expired auth sessions are deleted
after one day. Invoice retention requires an operator-approved policy and purge process;
automatic invoice deletion is deliberately absent. Deleted data can persist in backups
until their configured expiry. Document this in a real privacy notice.

## External AI

Organization opt-in and a configured API key are both required. Without either, the
analyst uses deterministic results. Only aggregate totals, fixed intent categories and
finding codes are transmitted. Raw user questions are classified locally and discarded;
names, credentials and tenant/user IDs are not transmitted. No prompts/responses are
stored by the app. The SDK sets `store=false`, a 12-second timeout and no retries.

The currently configured provider is Groq through its OpenAI-compatible endpoint.
Set `GROQ_API_KEY` and `AI_MODEL` to enable it. The provider returns a bounded list of
evidence IDs and an explanation. Unknown IDs, malformed output and numeric tokens
not present in selected evidence are rejected. Only the selected server-owned evidence
is rendered; model prose is discarded because even known amounts can be attributed
to the wrong period. The model helps select and order findings, rather than authoring
financial claims. Financial calculations and invoice writes remain entirely in Java.
No model-controlled SQL, write tool or autonomous action is exposed.

`store=false` does **not** promise zero provider retention. Consult the configured
provider's current data handling policies, account settings, processing agreement and
regional routing. OpenAI's policies do not describe data handling by Groq merely because
the application uses the OpenAI Java SDK.
No live provider was used for verification; transport tests use a local fake HTTP endpoint.

No GDPR compliance, legal invoice validity or production security certification is claimed.
