ALTER TABLE campaigns
    ADD COLUMN media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE'
        CHECK (media_type IN ('IMAGE', 'VIDEO'));

ALTER TABLE campaign_drafts
    ADD COLUMN media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE'
        CHECK (media_type IN ('IMAGE', 'VIDEO'));
