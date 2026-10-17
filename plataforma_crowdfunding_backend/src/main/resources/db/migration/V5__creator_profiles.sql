CREATE TABLE creator_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    display_name VARCHAR(120) NOT NULL,
    bio TEXT,
    professional_title VARCHAR(120),
    phone VARCHAR(30),
    bank_account_type VARCHAR(30) NOT NULL CHECK (bank_account_type IN ('SAVINGS', 'CHECKING')),
    bank_name VARCHAR(120) NOT NULL,
    bank_account_number VARCHAR(512) NOT NULL,
    bank_account_holder VARCHAR(160) NOT NULL,
    bank_account_document VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE creator_profile_links (
    id UUID PRIMARY KEY,
    creator_profile_id UUID NOT NULL REFERENCES creator_profiles(id) ON DELETE CASCADE,
    link_type VARCHAR(30) NOT NULL CHECK (link_type IN ('PORTFOLIO', 'PROFESSIONAL_NETWORK', 'OTHER_PROJECT')),
    label VARCHAR(120),
    url VARCHAR(1000) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_creator_profile_links_url UNIQUE (creator_profile_id, url)
);

CREATE INDEX ix_creator_profile_links_profile_order
    ON creator_profile_links (creator_profile_id, sort_order);
