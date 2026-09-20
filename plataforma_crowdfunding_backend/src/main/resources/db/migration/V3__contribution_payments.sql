ALTER TABLE contributions DROP CONSTRAINT IF EXISTS contributions_status_check;
ALTER TABLE contributions ADD COLUMN reward_id UUID;
ALTER TABLE contributions ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE contributions ADD COLUMN idempotency_key VARCHAR(128);
UPDATE contributions SET idempotency_key = id::varchar WHERE idempotency_key IS NULL;
ALTER TABLE contributions ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE contributions ADD CONSTRAINT fk_contributions_reward FOREIGN KEY (reward_id) REFERENCES campaign_rewards(id);
ALTER TABLE contributions ADD CONSTRAINT fk_contributions_sponsor FOREIGN KEY (sponsor_id) REFERENCES users(id);
ALTER TABLE contributions ADD CONSTRAINT uk_contributions_sponsor_idempotency UNIQUE (sponsor_id, idempotency_key);
ALTER TABLE contributions ADD CONSTRAINT contributions_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'REFUNDED'));
CREATE INDEX ix_contributions_sponsor_id ON contributions (sponsor_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    contribution_id UUID NOT NULL REFERENCES contributions(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    provider_payment_id VARCHAR(160) UNIQUE,
    amount DECIMAL(19,2) NOT NULL,
    platform_fee DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    failure_code VARCHAR(80),
    failure_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_payments_contribution_id ON payments (contribution_id);

CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    payment_id UUID REFERENCES payments(id),
    provider_event_id VARCHAR(160) NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);