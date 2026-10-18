package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface SupportedCampaignProjection {
    String getCampaignId();
    String getImageUrl();
    String getMediaType();
    String getTitle();
    BigDecimal getAmountContributedByMe();
    String getCurrency();
    BigDecimal getRaisedAmount();
    BigDecimal getGoalAmount();
    OffsetDateTime getLastContributionAt();
    String getStatusCode();
}
