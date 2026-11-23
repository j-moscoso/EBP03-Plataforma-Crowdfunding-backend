ALTER TABLE campaigns
    ADD COLUMN funding_model VARCHAR(20) NOT NULL DEFAULT 'FIXED'
    CHECK (funding_model IN ('FIXED', 'FLEXIBLE'));

ALTER TABLE campaign_drafts
    ADD COLUMN funding_model VARCHAR(20) NOT NULL DEFAULT 'FIXED'
    CHECK (funding_model IN ('FIXED', 'FLEXIBLE'));

ALTER TABLE contributions DROP CONSTRAINT IF EXISTS contributions_status_check;
ALTER TABLE contributions ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE contributions ADD CONSTRAINT contributions_status_check
    CHECK (status IN ('PENDING', 'HELD', 'CONFIRMED', 'FAILED', 'REFUNDED'));

ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS ix_campaigns_status_deadline ON campaigns(status, deadline);
CREATE INDEX IF NOT EXISTS ix_contributions_campaign_status ON contributions(campaign_id, status);
CREATE INDEX IF NOT EXISTS ix_payments_contribution_status ON payments(contribution_id, status);
CREATE INDEX IF NOT EXISTS ix_transaction_receipts_contribution_status ON transaction_receipts(contribution_id, status);
