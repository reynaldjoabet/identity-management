-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(256) NOT NULL,
    normalized_name VARCHAR(256) NOT NULL,
    concurrency_stamp UUID DEFAULT uuidv7(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT uq_roles_normalized_name UNIQUE (normalized_name)
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_name VARCHAR(256) NOT NULL,
    normalized_user_name VARCHAR(256) NOT NULL,
    email CITEXT NOT NULL,
    email_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash TEXT,
    security_stamp UUID DEFAULT uuidv7(),
    concurrency_stamp UUID DEFAULT uuidv7(),
    phone_number VARCHAR(20),
    phone_number_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    lockout_end TIMESTAMPTZ,
    lockout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    access_failed_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_user_name UNIQUE (user_name),
    CONSTRAINT uq_users_normalized_user_name UNIQUE (normalized_user_name),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE role_claims (
    id SERIAL PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    claim_type VARCHAR(256) NOT NULL,
    claim_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_claims (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    claim_type VARCHAR(256) NOT NULL,
    claim_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- External login associations (Google, Facebook, etc.)
CREATE TABLE user_logins (
    login_provider VARCHAR(128) NOT NULL,
    provider_key VARCHAR(256) NOT NULL,
    provider_display_name VARCHAR(256),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (login_provider, provider_key)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_tokens (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    login_provider VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, login_provider, name)
);

-- Indexes for foreign keys and common queries
CREATE INDEX idx_role_claims_role_id ON role_claims(role_id);
CREATE INDEX idx_user_claims_user_id ON user_claims(user_id);
CREATE INDEX idx_user_logins_user_id ON user_logins(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_users_email ON users(email);

-- Function to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with updated_at
CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


-- users (1) ─────────┬─── user_roles (*) ───── roles (1)
--                    ├─── user_claims (*)
--                    ├─── user_logins (*)
--                    └─── user_tokens (*)
--
-- roles (1) ─────────┬─── role_claims (*)
--                    └─── user_roles (*)