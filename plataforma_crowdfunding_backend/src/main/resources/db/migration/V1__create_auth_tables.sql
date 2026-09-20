CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100),
    role VARCHAR(20) NOT NULL CHECK (role IN ('CREATOR', 'SPONSOR')),
    verification_status VARCHAR(20) NOT NULL CHECK (verification_status IN ('PENDING', 'VERIFIED')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX ix_users_email ON users (email);

CREATE TABLE social_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email_at_provider VARCHAR(320),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX ix_social_accounts_user_id ON social_accounts (user_id);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    CONSTRAINT uk_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_sessions_user_id ON sessions (user_id);
CREATE INDEX ix_sessions_expires_at ON sessions (expires_at);
CREATE INDEX ix_sessions_revoked_at ON sessions (revoked_at);
