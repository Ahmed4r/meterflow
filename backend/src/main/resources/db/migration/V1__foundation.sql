CREATE TABLE app_user (
 id uuid PRIMARY KEY, email varchar(254) UNIQUE NOT NULL, password_hash varchar(100) NOT NULL,
 name varchar(100) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE organization (
 id uuid PRIMARY KEY, name varchar(100) NOT NULL, ai_enabled boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE membership (
 organization_id uuid NOT NULL REFERENCES organization(id), user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 role varchar(10) NOT NULL CHECK(role IN ('OWNER','ADMIN','VIEWER')), PRIMARY KEY(organization_id,user_id)
);
CREATE INDEX membership_user ON membership(user_id);
CREATE TABLE auth_session (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 refresh_hash varchar(64) UNIQUE NOT NULL, expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false
);
CREATE TABLE api_key (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), name varchar(80) NOT NULL,
 token_hash varchar(64) UNIQUE NOT NULL, prefix varchar(16) NOT NULL, revoked boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE customer (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), name varchar(100) NOT NULL,
 reference varchar(100) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(organization_id,id), UNIQUE(organization_id,reference)
);
CREATE TABLE plan (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), name varchar(80) NOT NULL,
 currency char(3) NOT NULL CHECK(currency='EUR'), base_price numeric(19,2) NOT NULL CHECK(base_price>=0),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(organization_id,id)
);
CREATE TABLE pricing_tier (
 id uuid PRIMARY KEY, plan_id uuid NOT NULL REFERENCES plan(id), position integer NOT NULL CHECK(position>=0),
 up_to bigint CHECK(up_to>0), unit_price numeric(19,6) NOT NULL CHECK(unit_price>=0), UNIQUE(plan_id,position)
);
CREATE TABLE subscription (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), customer_id uuid NOT NULL,
 plan_id uuid NOT NULL, status varchar(12) NOT NULL CHECK(status IN ('ACTIVE','CANCELLED')),
 starts_at timestamptz NOT NULL, cancelled_at timestamptz, version bigint NOT NULL DEFAULT 0,
 UNIQUE(organization_id,id), FOREIGN KEY(organization_id,customer_id) REFERENCES customer(organization_id,id),
 FOREIGN KEY(organization_id,plan_id) REFERENCES plan(organization_id,id)
);
CREATE TABLE subscription_history (
 id uuid PRIMARY KEY, subscription_id uuid NOT NULL REFERENCES subscription(id), action varchar(30) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE usage_event (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), subscription_id uuid NOT NULL,
 idempotency_key varchar(128) NOT NULL, service varchar(60) NOT NULL, quantity bigint NOT NULL CHECK(quantity>0 AND quantity<=1000000000),
 occurred_at timestamptz NOT NULL, received_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(organization_id,idempotency_key), FOREIGN KEY(organization_id,subscription_id) REFERENCES subscription(organization_id,id)
);
CREATE INDEX usage_period ON usage_event(organization_id,occurred_at,subscription_id);
CREATE INDEX usage_subscription_period ON usage_event(subscription_id,occurred_at);
CREATE TABLE invoice (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), subscription_id uuid NOT NULL,
 period_start date NOT NULL, period_end date NOT NULL, currency char(3) NOT NULL CHECK(currency='EUR'),
 total numeric(19,2) NOT NULL CHECK(total>=0), status varchar(10) NOT NULL CHECK(status IN ('OPEN','PAID')),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(subscription_id,period_start), UNIQUE(organization_id,id),
 CHECK(period_end>period_start), FOREIGN KEY(organization_id,subscription_id) REFERENCES subscription(organization_id,id)
);
CREATE INDEX invoice_tenant_period ON invoice(organization_id,period_start);
CREATE TABLE invoice_line (
 id uuid PRIMARY KEY, invoice_id uuid NOT NULL REFERENCES invoice(id), description varchar(120) NOT NULL,
 quantity bigint NOT NULL CHECK(quantity>=0), unit_price numeric(19,6) NOT NULL,
 amount numeric(19,2) NOT NULL, position integer NOT NULL, UNIQUE(invoice_id,position)
);
