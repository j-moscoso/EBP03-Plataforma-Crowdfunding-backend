CREATE TABLE campaign_drafts (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL,
    title VARCHAR(160),
    description TEXT,
    goal_amount DECIMAL(19,2),
    duration_days INTEGER,
    category VARCHAR(80),
    media_url VARCHAR(500),
    completion_percentage INTEGER NOT NULL DEFAULT 0,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_campaign_drafts_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE INDEX ix_campaign_drafts_creator_id ON campaign_drafts (creator_id);

CREATE TABLE draft_rewards (
    id UUID PRIMARY KEY,
    draft_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT,
    minimum_amount DECIMAL(19,2) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_draft_rewards_draft FOREIGN KEY (draft_id) REFERENCES campaign_drafts(id) ON DELETE CASCADE
);

CREATE INDEX ix_draft_rewards_draft_id ON draft_rewards (draft_id);

CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    goal_amount DECIMAL(19,2) NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    category VARCHAR(80) NOT NULL,
    media_url VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ACTIVE', 'SUCCESSFUL', 'FAILED', 'CANCELLED')),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_campaigns_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE INDEX ix_campaigns_creator_id ON campaigns (creator_id);
CREATE INDEX ix_campaigns_status ON campaigns (status);
CREATE INDEX ix_campaigns_category ON campaigns (category);

CREATE TABLE campaign_rewards (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT,
    minimum_amount DECIMAL(19,2) NOT NULL,
    quantity INTEGER,
    claimed_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_campaign_rewards_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE
);

CREATE INDEX ix_campaign_rewards_campaign_id ON campaign_rewards (campaign_id);

CREATE TABLE campaign_updates (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(140) NOT NULL,
    body TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_campaign_updates_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_updates_author FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX ix_campaign_updates_campaign_id ON campaign_updates (campaign_id);

CREATE TABLE contributions (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL,
    sponsor_id UUID NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_contributions_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE
);

CREATE INDEX ix_contributions_campaign_id ON contributions (campaign_id);
CREATE INDEX ix_contributions_status ON contributions (status);
