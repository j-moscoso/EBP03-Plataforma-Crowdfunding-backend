CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_ip VARCHAR(64),
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX ix_password_reset_tokens_user_created ON password_reset_tokens (user_id, created_at);
CREATE INDEX ix_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

CREATE TABLE password_reset_rate_limits (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    ip_address VARCHAR(64) NOT NULL,
    rate_key VARCHAR(320) NOT NULL UNIQUE,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP WITH TIME ZONE,
    last_event_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_password_reset_rate_limits_ip ON password_reset_rate_limits (ip_address);
CREATE INDEX ix_password_reset_rate_limits_user ON password_reset_rate_limits (user_id);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    target_user_id UUID REFERENCES users(id),
    event_type VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_logs_target_created ON audit_logs (target_user_id, created_at);
CREATE INDEX ix_audit_logs_event_created ON audit_logs (event_type, created_at);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    campaign_id UUID,
    campaign_update_id UUID,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_user_id, created_at);
CREATE UNIQUE INDEX uk_notifications_recipient_type_target
    ON notifications (recipient_user_id, type, campaign_update_id);
