CREATE INDEX ix_contributions_sponsor_status_campaign
    ON contributions (sponsor_id, status, campaign_id);

CREATE INDEX ix_contributions_campaign_status
    ON contributions (campaign_id, status);
