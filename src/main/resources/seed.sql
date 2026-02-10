START TRANSACTION;

---------------------------------------------------------------------------
-- 1. IDENTITY RESOURCES & CLAIMS
---------------------------------------------------------------------------
INSERT INTO identity_resources (name, display_name, description, enabled, required, emphasize, show_in_discovery_document)
VALUES 
    ('openid', 'Your user identifier', 'Required for OIDC', true, true, false, true),
    ('profile', 'User profile', 'Name, nickname, etc.', true, false, true, true),
    ('email', 'Email', 'Your email address', true, false, false, true);

INSERT INTO identity_resource_claims (identity_resource_id, type)
SELECT id, c.t FROM identity_resources i, (VALUES ('sub'), ('name'), ('given_name'), ('email'), ('email_verified')) AS c(t)
WHERE (i.name = 'openid' AND c.t = 'sub') 
   OR (i.name = 'profile' AND c.t IN ('name', 'given_name'))
   OR (i.name = 'email' AND c.t IN ('email', 'email_verified'));

INSERT INTO identity_resource_properties (identity_resource_id, key, value)
SELECT id, 'category', 'standard' FROM identity_resources WHERE name = 'openid';

---------------------------------------------------------------------------
-- 2. API SCOPES & PROPERTIES
---------------------------------------------------------------------------
INSERT INTO api_scopes (name, display_name, description, enabled, required, emphasize, show_in_discovery_document)
VALUES 
    ('api.read', 'Read Access', 'Read-only access to data', true, false, false, true),
    ('api.write', 'Write Access', 'Write access to data', true, false, true, true),
    ('api.admin', 'Admin Access', 'Critical administrative tasks', true, false, true, false);

INSERT INTO api_scope_claims (scope_id, type)
SELECT id, 'tenant_id' FROM api_scopes WHERE name = 'api.admin';

INSERT INTO api_scope_properties (scope_id, key, value)
SELECT id, 'priority', 'high' FROM api_scopes WHERE name = 'api.admin';

---------------------------------------------------------------------------
-- 3. API RESOURCES, SECRETS & PROPERTIES
---------------------------------------------------------------------------
INSERT INTO api_resources (name, display_name, description, enabled)
VALUES 
    ('payment_service', 'Payment Gateway', 'Handles all credit card logic', true),
    ('inventory_service', 'Warehouse API', 'Internal stock management', true);

INSERT INTO api_resource_scopes (api_resource_id, scope)
SELECT r.id, s.name FROM api_resources r, api_scopes s 
WHERE (r.name = 'payment_service' AND s.name IN ('api.read', 'api.write'))
   OR (r.name = 'inventory_service' AND s.name = 'api.admin');

INSERT INTO api_resource_claims (api_resource_id, type)
SELECT id, 'transaction_limit' FROM api_resources WHERE name = 'payment_service';

INSERT INTO api_resource_secrets (api_resource_id, value, type, description)
SELECT id, 'K7gskx4ne0geicudEku9m/+WYdxTe0as60S7Fv39Syo=', 'SharedSecret', 'Secret for Introspection' 
FROM api_resources WHERE name = 'payment_service';

INSERT INTO api_resource_properties (api_resource_id, key, value)
SELECT id, 'environment', 'production' FROM api_resources WHERE name = 'payment_service';

---------------------------------------------------------------------------
-- 4. CLIENTS - The core entities
---------------------------------------------------------------------------
INSERT INTO clients (client_id, client_name, protocol_type, require_pkce, access_token_lifetime, require_dpop)
VALUES 
    ('web_app', 'Marketing Website', 'oidc', true, 3600, false),
    ('mobile_app', 'iOS/Android Banking', 'oidc', true, 1800, true), -- Advanced: DPoP enabled
    ('service_bot', 'Background Worker', 'oidc', false, 86400, false);

---------------------------------------------------------------------------
-- 5. CLIENT RELATIONSHIP TABLES (Child Tables)
---------------------------------------------------------------------------

-- 5.1 Grant Types
INSERT INTO client_grant_types (client_id, grant_type)
SELECT id, 'authorization_code' FROM clients WHERE client_id IN ('web_app', 'mobile_app');
INSERT INTO client_grant_types (client_id, grant_type)
SELECT id, 'client_credentials' FROM clients WHERE client_id = 'service_bot';

-- 5.2 Redirect URIs
INSERT INTO client_redirect_uris (client_id, redirect_uri)
SELECT id, 'https://marketing.com/callback' FROM clients WHERE client_id = 'web_app';
INSERT INTO client_redirect_uris (client_id, redirect_uri)
SELECT id, 'com.banking.app://callback' FROM clients WHERE client_id = 'mobile_app';

-- 5.3 Post Logout Redirect URIs
INSERT INTO client_post_logout_redirect_uris (client_id, post_logout_redirect_uri)
SELECT id, 'https://marketing.com/home' FROM clients WHERE client_id = 'web_app';

-- 5.4 Client Scopes
INSERT INTO client_scopes (client_id, scope)
SELECT id, 'openid' FROM clients;
INSERT INTO client_scopes (client_id, scope)
SELECT id, 'api.read' FROM clients WHERE client_id IN ('web_app', 'mobile_app');
INSERT INTO client_scopes (client_id, scope)
SELECT id, 'api.admin' FROM clients WHERE client_id = 'service_bot';

-- 5.5 Client Secrets
INSERT INTO client_secrets (client_id, value, type, description)
SELECT id, 'K7gskx4ne0geicudEku9m/+WYdxTe0as60S7Fv39Syo=', 'SharedSecret', 'Standard Secret' 
FROM clients WHERE client_id IN ('web_app', 'service_bot');

-- 5.6 Client Claims (Hardcoded claims in the token)
INSERT INTO client_claims (client_id, type, value)
SELECT id, 'client_tier', 'premium' FROM clients WHERE client_id = 'mobile_app';

-- 5.7 CORS Origins
INSERT INTO client_cors_origins (client_id, origin)
SELECT id, 'https://marketing.com' FROM clients WHERE client_id = 'web_app';

-- 5.8 IDP Restrictions (Force specific login methods)
INSERT INTO client_idp_restrictions (client_id, provider)
SELECT id, 'google' FROM clients WHERE client_id = 'web_app';

-- 5.9 Client Properties (Custom metadata)
INSERT INTO client_properties (client_id, key, value)
SELECT id, 'contact_email', 'devops@company.com' FROM clients WHERE client_id = 'service_bot';

---------------------------------------------------------------------------
-- 6. IDENTITY PROVIDERS (External Auth)
---------------------------------------------------------------------------
INSERT INTO identity_providers (scheme, display_name, enabled, type, properties)
VALUES 
    ('google', 'Google Login', true, 'oidc', '{"authority":"https://accounts.google.com"}'),
    ('oidc-okta', 'Okta Corporate', true, 'oidc', '{"authority":"https://okta.com/tenant"}');

COMMIT;