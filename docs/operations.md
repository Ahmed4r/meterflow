# Operations

## Deployment

Local Compose binds application/database ports to loopback. For deployment, put a TLS
reverse proxy in front of the frontend, set `APP_ORIGIN=https://your-domain`, set
`SECURE_COOKIE=true`, and keep PostgreSQL, Redis and the backend on a private network.
The frontend proxies `/api` to Spring, so the browser uses a single origin. Configure
the proxy's trusted-forwarding behavior explicitly; the application does not trust
arbitrary forwarded headers for rate-limit identity.

Use a managed secret store for JWT, database and provider credentials. Protect database
storage/backups with host or managed-service encryption. Use TLS for database/Redis
connections that cross hosts. Set `DEMO_SEED=false` for any real deployment. Disable
public OpenAPI using `SPRINGDOC_API_DOCS_ENABLED=false` and
`SPRINGDOC_SWAGGER_UI_ENABLED=false` if documentation should not be exposed.

Examples for a TLS proxy are in `deploy/Caddyfile`. This is a deployment template;
certificate issuance and a real host have not been verified.

## Health and billing

`GET /actuator/health` reports health without details. Monitor health, 5xx/429 rates,
database disk/connection capacity, Redis availability, and overdue billing periods.
Hourly billing processes up to 100 missing historical periods in oldest-first order.
Each invoice is a separate transaction; failures remain eligible for retry. Multiple
application instances may run the job safely due to subscription locks and uniqueness.

No payment provider is integrated. Marking an invoice paid records an operator assertion.
An audit ledger and reconciliation workflow are required before handling real payments.

## Backup and restore

Use a restricted operator account and encrypted destinations. Example shell commands:

```sh
# Dump inside the container to avoid shell binary-redirection issues on Windows.
docker compose exec -T postgres pg_dump -U meterflow -d meterflow -Fc -f /tmp/meterflow.dump
docker compose cp postgres:/tmp/meterflow.dump ./meterflow.dump
```

The dump can contain personal data. Move it to encrypted, access-controlled storage,
record its expiry, and remove temporary copies. Do not commit it. Test restoration into
a separate database before relying on a backup:

```sh
docker compose exec -T postgres createdb -U meterflow meterflow_restore
docker compose cp ./meterflow.dump postgres:/tmp/meterflow-restore.dump
docker compose exec -T postgres pg_restore -U meterflow -d meterflow_restore --no-owner /tmp/meterflow-restore.dump
docker compose exec -T postgres psql -U meterflow -d meterflow_restore -c 'select count(*) from invoice;'
```

Point an isolated backend at the restored database; validate migrations, invoice totals
and representative queries. Never restore over the live database without a reviewed
recovery plan. Establish RPO/RTO, off-host replication, backup retention and periodic
restore drills appropriate to the deployment. Redis contains ephemeral rate-limit
counters only and does not require financial-state restoration.

## Known scaling limits

Ingestion serializes per subscription, deliberately favoring correctness over peak
throughput. Very hot subscriptions need measured redesign. Aggregate analytics are
uncached to avoid stale financial estimates; appropriate usage indexes are in Flyway.
Raw event cleanup changes historical usage charts but never issued invoice totals.
Plan selectors currently load up to 100 plans and customer/subscription assignment
selectors the first 50 records. Expand these into server-side search selectors before
using larger catalogs. This is documented rather than silently presented as unlimited.
