CREATE TABLE creator_subscriptions (
    id UUID PRIMARY KEY,
    sponsor_id UUID NOT NULL REFERENCES users(id),
    creator_id UUID NOT NULL REFERENCES users(id),
    payment_method_ref VARCHAR(160) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    next_charge_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_charge_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_creator_subscriptions_amount CHECK (amount > 0),
    CONSTRAINT chk_creator_subscriptions_currency
        CHECK (currency = UPPER(currency) AND CHAR_LENGTH(currency) = 3),
    CONSTRAINT chk_creator_subscriptions_status
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'FAILED'))
);

CREATE INDEX ix_creator_subscriptions_status_next_charge
    ON creator_subscriptions(status, next_charge_at);
CREATE INDEX ix_creator_subscriptions_sponsor_created
    ON creator_subscriptions(sponsor_id, created_at DESC);

ALTER TABLE contributions
    ALTER COLUMN campaign_id DROP NOT NULL;
ALTER TABLE contributions
    ADD COLUMN creator_id UUID;
ALTER TABLE contributions
    ADD COLUMN subscription_id UUID;
ALTER TABLE contributions
    ADD CONSTRAINT fk_contributions_creator FOREIGN KEY (creator_id) REFERENCES users(id);
ALTER TABLE contributions
    ADD CONSTRAINT fk_contributions_subscription
        FOREIGN KEY (subscription_id) REFERENCES creator_subscriptions(id);
ALTER TABLE contributions
    ADD CONSTRAINT chk_contributions_campaign_or_creator
        CHECK (campaign_id IS NOT NULL OR creator_id IS NOT NULL);
CREATE INDEX ix_contributions_subscription_id ON contributions(subscription_id);
CREATE INDEX ix_contributions_creator_id ON contributions(creator_id);

ALTER TABLE payments
    ALTER COLUMN contribution_id DROP NOT NULL;
ALTER TABLE payments
    ADD COLUMN subscription_charge_attempt_id UUID;

CREATE TABLE subscription_charge_attempts (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES creator_subscriptions(id),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    contribution_id UUID REFERENCES contributions(id),
    payment_id UUID UNIQUE REFERENCES payments(id),
    failure_code VARCHAR(80),
    failure_message VARCHAR(500),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_subscription_charge_attempts_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_subscription_charge_attempts_subscription
    ON subscription_charge_attempts(subscription_id, scheduled_at);

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_subscription_attempt
        FOREIGN KEY (subscription_charge_attempt_id) REFERENCES subscription_charge_attempts(id);
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_subscription_attempt UNIQUE (subscription_charge_attempt_id);
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_contribution_or_subscription_attempt
        CHECK (contribution_id IS NOT NULL OR subscription_charge_attempt_id IS NOT NULL);

ALTER TABLE notifications
    ADD COLUMN contribution_id UUID;
ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_contribution
        FOREIGN KEY (contribution_id) REFERENCES contributions(id);
CREATE UNIQUE INDEX ux_notifications_contribution_type
    ON notifications(contribution_id, type);
