CREATE TABLE campaign_discussion_comments (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    parent_comment_id UUID NULL REFERENCES campaign_discussion_comments(id),
    body TEXT NOT NULL,
    is_official_answer BOOLEAN NOT NULL DEFAULT FALSE,
    is_faq BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_campaign_discussion_body_not_blank CHECK (TRIM(body) <> '')
);

CREATE INDEX ix_campaign_discussion_campaign_created
    ON campaign_discussion_comments(campaign_id, created_at);

CREATE INDEX ix_campaign_discussion_campaign_faq_official
    ON campaign_discussion_comments(campaign_id, is_faq, is_official_answer);
