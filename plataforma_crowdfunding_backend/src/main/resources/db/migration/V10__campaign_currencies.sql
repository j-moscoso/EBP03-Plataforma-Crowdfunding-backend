-- Existing campaigns and drafts remain NULL until their authoritative currency
-- is reviewed. New campaigns must receive currency before publication.
ALTER TABLE campaigns ADD COLUMN currency VARCHAR(3);
ALTER TABLE campaign_drafts ADD COLUMN currency VARCHAR(3);
