CREATE TABLE campaign_update_media (
    id UUID PRIMARY KEY,
    update_id UUID NOT NULL REFERENCES campaign_updates(id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    url VARCHAR(1000) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_campaign_update_media_update_order
    ON campaign_update_media (update_id, sort_order);

CREATE INDEX ix_campaign_updates_campaign_published
    ON campaign_updates (campaign_id, published_at DESC, id DESC);

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_campaign
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id);

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_campaign_update
    FOREIGN KEY (campaign_update_id) REFERENCES campaign_updates(id) ON DELETE CASCADE;
