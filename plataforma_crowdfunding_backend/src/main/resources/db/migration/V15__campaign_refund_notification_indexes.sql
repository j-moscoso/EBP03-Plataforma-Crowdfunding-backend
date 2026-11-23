CREATE INDEX IF NOT EXISTS ix_notifications_recipient_read_created
    ON notifications(recipient_user_id, read_at, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_recipient_contribution_type
    ON notifications(recipient_user_id, contribution_id, type);
