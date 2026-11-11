# API contract and examples

Base URL: `http://localhost:8080/api/v1`. Generated OpenAPI is served at `/v3/api-docs`.
JSON errors use RFC 9457 Problem Details. Invalid data → 400; unauthenticated → 401;
non-member/read-only writes → 403; absent tenant resource → 404; replay conflict → 409;
rate limit → 429; rate limiter unavailable → 503. Never retry a changed event with the same key.

All organization endpoints require `Authorization: Bearer $ACCESS_TOKEN`. A supplied
organization ID is checked against the authenticated user's membership on every request.
Roles: VIEWER reads, ADMIN manages billing/settings/keys, OWNER additionally manages members.

## Authentication

```sh
curl -s http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"engineer@example.test","password":"choose-your-own-long-password","name":"Synthetic Engineer","organization":"Test Workspace"}'

curl -c cookies.txt http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@meterflow.test","password":"YOUR_LOCAL_DEMO_PASSWORD"}'

curl -b cookies.txt -c cookies.txt -X POST \
  -H 'Origin: http://localhost:3000' http://localhost:8080/api/v1/auth/refresh
```

The response contains `accessToken`; the refresh token is an HttpOnly cookie. Access
expires after 15 minutes. The refresh session expires after 14 days, even if rotated.
Refresh and logout require the exact configured browser origin. Remove local cookie
files after testing and never commit them.

## Ingestion

Create an API key under organization settings. Copy it once and store it securely.
Retrieve a subscription ID from `GET /organizations/{org}/subscriptions`.

```sh
curl -i http://localhost:8080/api/v1/usage-events \
  -H "X-API-Key: $METERFLOW_API_KEY" \
  -H 'Idempotency-Key: request-2026-001' \
  -H 'Content-Type: application/json' \
  -d '{"subscriptionId":"YOUR_SUBSCRIPTION_UUID","service":"search-api","quantity":150,"occurredAt":"2026-11-09T00:00:00Z"}'
```

Use a current timestamp in the subscription's active window. There is intentionally
no organization ID in this request. The key determines the tenant. Successful
requests return `{id, duplicate}`; retries return the same ID. Quantities are positive
integers up to one billion per event. Service identifiers use letters, digits, dots,
underscores or hyphens. Ingestion is limited to 600 attempts per minute per tenant.

## Resource routes

| Route | Methods | Purpose |
|---|---|---|
| `/me` | GET, PATCH, DELETE | Profile, name update, password-confirmed deletion |
| `/me/export` | GET | Personal JSON export without secrets |
| `/organizations/{org}` | PATCH | Name and external-AI opt-in |
| `/organizations/{org}/members` | GET, POST | List / add or update an existing registered user |
| `/organizations/{org}/api-keys` | GET, POST | List metadata / create key |
| `/organizations/{org}/api-keys/{id}` | DELETE | Revoke key |
| `/organizations/{org}/customers` | GET, POST | List/search / create |
| `/organizations/{org}/plans` | GET, POST | List / create immutable graduated plan |
| `/organizations/{org}/subscriptions` | GET, POST | List / assign plan |
| `/organizations/{org}/subscriptions/{id}/cancel` | POST | Idempotent cancellation |
| `/organizations/{org}/subscriptions/{id}/history` | GET | Lifecycle events |
| `/organizations/{org}/invoices` | GET, POST | List/filter / close a completed month |
| `/organizations/{org}/invoices/{id}` | GET | Snapshot and line items |
| `/organizations/{org}/invoices/{id}/pdf` | GET | PDF |
| `/organizations/{org}/invoices/{id}/paid` | POST | Manual paid status, no collection |
| `/organizations/{org}/analytics?month=YYYY-MM-01` | GET | Deterministic monthly facts |
| `/organizations/{org}/analytics/estimate?planId=UUID&units=1000` | GET | Graduated plan estimate |
| `/organizations/{org}/analyst` | POST | Read-only explanation of authorized facts |

Customers, subscriptions and invoices use zero-based `page` with 50 rows per page.
Customer `search` matches names or references. Invoice `status` is empty, OPEN or PAID.
Plans are capped at 100 results; a larger catalog needs additional pagination before use.

Plan body:

```json
{"name":"Scale","basePrice":"99.00","tiers":[{"upTo":50000,"unitPrice":"0.004000"},{"upTo":null,"unitPrice":"0.002500"}]}
```

The final tier is unbounded; other bounds must strictly increase. Pricing is graduated,
not volume pricing. Invoice generation body: `{ "subscriptionId": "UUID", "month": "YYYY-MM-01" }`.
Analyst body: `{ "question": "Which plan is more cost-effective?", "month": "YYYY-MM-01" }`.
