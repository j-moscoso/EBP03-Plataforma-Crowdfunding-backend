package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import java.math.BigDecimal;

public interface CampaignSponsorRankingProjection {
    String getSponsorId();
    String getSponsorName();
    BigDecimal getTotalAmount();
}
