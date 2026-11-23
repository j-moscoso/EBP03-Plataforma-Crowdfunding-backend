CREATE TABLE subscription_campaign_allocations (
    id UUID PRIMARY KEY,
    subscription_charge_attempt_id UUID NOT NULL
        REFERENCES subscription_charge_attempts(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    sponsor_id UUID NOT NULL REFERENCES users(id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL
        CHECK (currency = UPPER(currency) AND CHAR_LENGTH(currency) = 3),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_subscription_campaign_allocation_attempt_campaign
        UNIQUE (subscription_charge_attempt_id, campaign_id)
);

CREATE INDEX ix_subscription_campaign_allocations_campaign_currency
    ON subscription_campaign_allocations(campaign_id, currency);
CREATE INDEX ix_subscription_campaign_allocations_sponsor_campaign
    ON subscription_campaign_allocations(sponsor_id, campaign_id);
