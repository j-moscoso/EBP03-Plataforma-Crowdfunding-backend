ALTER TABLE campaign_rewards
    ADD COLUMN estimated_delivery_date DATE;

ALTER TABLE campaign_rewards
    ADD CONSTRAINT chk_campaign_rewards_minimum_amount
        CHECK (minimum_amount > 0);

ALTER TABLE campaign_rewards
    ADD CONSTRAINT chk_campaign_rewards_quantity
        CHECK (quantity IS NULL OR quantity > 0);

ALTER TABLE campaign_rewards
    ADD CONSTRAINT chk_campaign_rewards_claimed_quantity
        CHECK (claimed_quantity >= 0);

ALTER TABLE draft_rewards
    ADD COLUMN quantity INTEGER;

ALTER TABLE draft_rewards
    ADD COLUMN estimated_delivery_date DATE;

ALTER TABLE draft_rewards
    ADD CONSTRAINT chk_draft_rewards_quantity
        CHECK (quantity IS NULL OR quantity > 0);

ALTER TABLE draft_rewards
    ADD CONSTRAINT chk_draft_rewards_minimum_amount
        CHECK (minimum_amount > 0);
