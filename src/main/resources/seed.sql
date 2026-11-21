-- =====================================================================
-- Seed data for the OIDC / OAuth2 configuration store
-- Target: PostgreSQL 19
--
-- This script is idempotent: running it twice is a no-op, and running
-- it against a partially-seeded database fills in only what is missing.
--
-- That rests on PostgreSQL 19's ON CONFLICT ... DO SELECT. The problem
-- it solves: child rows need their parent's generated id, but the
-- parent may already exist. DO NOTHING returns nothing for a row that
-- conflicted, so a second run would hand the child inserts an empty
-- set. DO SELECT returns the existing row instead, so every run -- the
-- first and the fifth -- yields the full set of parent ids.
--
-- Each parent block is one statement: a chain of data-modifying CTEs.
-- Those run exactly once and to completion whether or not the final
-- query reads their output, and all of them see a single snapshot.
--
-- WARNING: the secret hashes below are fixed, published sample values,
-- fine for local development and worthless as secrets. Do not seed
-- them into any environment that matters.
-- =====================================================================

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. Identity resources, their claims and properties
-- ---------------------------------------------------------------------
WITH seeded AS (
    INSERT INTO identity_resources
        (name, display_name, description, enabled, required, emphasize, show_in_discovery_document)
    VALUES
        ('openid',  'Your user identifier', 'Required for OIDC',    true, true,  false, true),
        ('profile', 'User profile',         'Name, nickname, etc.', true, false, true,  true),
        ('email',   'Email',                'Your email address',   true, false, false, true)
    ON CONFLICT (name) DO SELECT
    RETURNING id, name
),
claims AS (
    INSERT INTO identity_resource_claims (identity_resource_id, type)
    SELECT s.id, c.type
    FROM seeded s
    -- An explicit JOIN on the resource name. The previous version used
    -- a comma cross join against a VALUES list and then filtered with a
    -- chain of ORed (name = ... AND type IN ...) predicates, which built
    -- the full product first and described the mapping twice.
    JOIN (VALUES
            ('openid',  'sub'),
            ('profile', 'name'),
            ('profile', 'given_name'),
            ('email',   'email'),
            ('email',   'email_verified')
         ) AS c(resource, type) ON c.resource = s.name
    ON CONFLICT (identity_resource_id, type) DO NOTHING
    RETURNING 1
),
props AS (
    INSERT INTO identity_resource_properties (identity_resource_id, key, value)
    SELECT s.id, 'category', 'standard'
    FROM seeded s
    WHERE s.name = 'openid'
    ON CONFLICT (identity_resource_id, key) DO NOTHING
    RETURNING 1
)
SELECT 'identity_resources' AS seeded_group, count(*) AS parents FROM seeded;

-- ---------------------------------------------------------------------
-- 2. API scopes, their claims and properties
-- ---------------------------------------------------------------------
WITH seeded AS (
    INSERT INTO api_scopes
        (name, display_name, description, enabled, required, emphasize, show_in_discovery_document)
    VALUES
        ('api.read',  'Read Access',  'Read-only access to data',     true, false, false, true),
        ('api.write', 'Write Access', 'Write access to data',         true, false, true,  true),
        ('api.admin', 'Admin Access', 'Critical administrative tasks', true, false, true, false)
    ON CONFLICT (name) DO SELECT
    RETURNING id, name
),
claims AS (
    INSERT INTO api_scope_claims (scope_id, type)
    SELECT s.id, 'tenant_id' FROM seeded s WHERE s.name = 'api.admin'
    ON CONFLICT (scope_id, type) DO NOTHING
    RETURNING 1
),
props AS (
    INSERT INTO api_scope_properties (scope_id, key, value)
    SELECT s.id, 'priority', 'high' FROM seeded s WHERE s.name = 'api.admin'
    ON CONFLICT (scope_id, key) DO NOTHING
    RETURNING 1
)
SELECT 'api_scopes' AS seeded_group, count(*) AS parents FROM seeded;

-- ---------------------------------------------------------------------
-- 3. API resources, their scopes, claims, secrets and properties
-- ---------------------------------------------------------------------
WITH seeded AS (
    INSERT INTO api_resources (name, display_name, description, enabled)
    VALUES
        ('payment_service',   'Payment Gateway', 'Handles all credit card logic', true),
        ('inventory_service', 'Warehouse API',   'Internal stock management',     true)
    ON CONFLICT (name) DO SELECT
    RETURNING id, name
),
scopes AS (
    INSERT INTO api_resource_scopes (api_resource_id, scope)
    SELECT s.id, m.scope
    FROM seeded s
    JOIN (VALUES
            ('payment_service',   'api.read'),
            ('payment_service',   'api.write'),
            ('inventory_service', 'api.admin')
         ) AS m(resource, scope) ON m.resource = s.name
    ON CONFLICT (api_resource_id, scope) DO NOTHING
    RETURNING 1
),
claims AS (
    INSERT INTO api_resource_claims (api_resource_id, type)
    SELECT s.id, 'transaction_limit' FROM seeded s WHERE s.name = 'payment_service'
    ON CONFLICT (api_resource_id, type) DO NOTHING
    RETURNING 1
),
secrets AS (
    INSERT INTO api_resource_secrets (api_resource_id, value, type, description)
    SELECT s.id,
           'K7gskx4ne0geicudEku9m/+WYdxTe0as60S7Fv39Syo=',  -- sample only
           'SharedSecret',
           'Secret for Introspection'
    FROM seeded s WHERE s.name = 'payment_service'
    ON CONFLICT (api_resource_id, type, value) DO NOTHING
    RETURNING 1
),
props AS (
    INSERT INTO api_resource_properties (api_resource_id, key, value)
    SELECT s.id, 'environment', 'production' FROM seeded s WHERE s.name = 'payment_service'
    ON CONFLICT (api_resource_id, key) DO NOTHING
    RETURNING 1
)
SELECT 'api_resources' AS seeded_group, count(*) AS parents FROM seeded;

-- ---------------------------------------------------------------------
-- 4. Clients and every child table hanging off them
--
-- One statement for all nine child tables, so a client and its grant
-- types, URIs, scopes, secrets and claims can never be half-seeded.
-- ---------------------------------------------------------------------
WITH seeded AS (
    INSERT INTO clients
        (client_id, client_name, protocol_type, require_pkce, access_token_lifetime, require_dpop)
    VALUES
        ('web_app',     'Marketing Website',   'oidc', true,  3600,  false),
        ('mobile_app',  'iOS/Android Banking', 'oidc', true,  1800,  true),   -- DPoP-bound tokens
        ('service_bot', 'Background Worker',   'oidc', false, 86400, false)
    ON CONFLICT (client_id) DO SELECT
    RETURNING id, client_id
),
grant_types AS (
    INSERT INTO client_grant_types (client_id, grant_type)
    SELECT s.id, m.grant_type
    FROM seeded s
    JOIN (VALUES
            ('web_app',     'authorization_code'),
            ('mobile_app',  'authorization_code'),
            ('service_bot', 'client_credentials')
         ) AS m(client, grant_type) ON m.client = s.client_id
    ON CONFLICT (client_id, grant_type) DO NOTHING
    RETURNING 1
),
redirect_uris AS (
    INSERT INTO client_redirect_uris (client_id, redirect_uri)
    SELECT s.id, m.uri
    FROM seeded s
    JOIN (VALUES
            ('web_app',    'https://marketing.com/callback'),
            ('mobile_app', 'com.banking.app://callback')
         ) AS m(client, uri) ON m.client = s.client_id
    ON CONFLICT (client_id, redirect_uri) DO NOTHING
    RETURNING 1
),
post_logout_uris AS (
    INSERT INTO client_post_logout_redirect_uris (client_id, post_logout_redirect_uri)
    SELECT s.id, 'https://marketing.com/home' FROM seeded s WHERE s.client_id = 'web_app'
    ON CONFLICT (client_id, post_logout_redirect_uri) DO NOTHING
    RETURNING 1
),
scopes AS (
    INSERT INTO client_scopes (client_id, scope)
    SELECT s.id, m.scope
    FROM seeded s
    JOIN (VALUES
            ('web_app',     'openid'),
            ('mobile_app',  'openid'),
            ('service_bot', 'openid'),
            ('web_app',     'api.read'),
            ('mobile_app',  'api.read'),
            ('service_bot', 'api.admin')
         ) AS m(client, scope) ON m.client = s.client_id
    ON CONFLICT (client_id, scope) DO NOTHING
    RETURNING 1
),
secrets AS (
    INSERT INTO client_secrets (client_id, value, type, description)
    SELECT s.id,
           'K7gskx4ne0geicudEku9m/+WYdxTe0as60S7Fv39Syo=',  -- sample only
           'SharedSecret',
           'Standard Secret'
    FROM seeded s
    WHERE s.client_id IN ('web_app', 'service_bot')
    ON CONFLICT (client_id, type, value) DO NOTHING
    RETURNING 1
),
claims AS (
    INSERT INTO client_claims (client_id, type, value)
    SELECT s.id, 'client_tier', 'premium' FROM seeded s WHERE s.client_id = 'mobile_app'
    ON CONFLICT (client_id, type, value) DO NOTHING
    RETURNING 1
),
cors AS (
    INSERT INTO client_cors_origins (client_id, origin)
    SELECT s.id, 'https://marketing.com' FROM seeded s WHERE s.client_id = 'web_app'
    ON CONFLICT (client_id, origin) DO NOTHING
    RETURNING 1
),
idp_restrictions AS (
    INSERT INTO client_idp_restrictions (client_id, provider)
    SELECT s.id, 'google' FROM seeded s WHERE s.client_id = 'web_app'
    ON CONFLICT (client_id, provider) DO NOTHING
    RETURNING 1
),
props AS (
    INSERT INTO client_properties (client_id, key, value)
    SELECT s.id, 'contact_email', 'devops@company.com' FROM seeded s WHERE s.client_id = 'service_bot'
    ON CONFLICT (client_id, key) DO NOTHING
    RETURNING 1
)
SELECT 'clients' AS seeded_group, count(*) AS parents FROM seeded;

-- ---------------------------------------------------------------------
-- 5. External identity providers
--
-- properties is jsonb now, so these blobs are parsed and validated on
-- the way in rather than at first login.
-- ---------------------------------------------------------------------
INSERT INTO identity_providers (scheme, display_name, enabled, type, properties)
VALUES
    ('google',    'Google Login',   true, 'oidc',
     '{"authority": "https://accounts.google.com"}'::jsonb),
    ('oidc-okta', 'Okta Corporate', true, 'oidc',
     '{"authority": "https://okta.com/tenant"}'::jsonb)
ON CONFLICT (scheme) DO NOTHING;

COMMIT;
