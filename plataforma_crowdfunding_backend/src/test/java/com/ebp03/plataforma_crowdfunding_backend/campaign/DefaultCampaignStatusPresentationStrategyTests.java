package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.DefaultCampaignStatusPresentationStrategy;
import org.junit.jupiter.api.Test;

class DefaultCampaignStatusPresentationStrategyTests {
    private final DefaultCampaignStatusPresentationStrategy strategy =
            new DefaultCampaignStatusPresentationStrategy();

    @Test
    void mapsInternalStatusesToPublicLabelsWithoutChangingTheirCodes() {
        assertEquals(new DefaultCampaignStatusPresentationStrategy.Presentation("ACTIVA", "ACTIVE"),
                strategy.present(CampaignStatus.ACTIVE));
        assertEquals(new DefaultCampaignStatusPresentationStrategy.Presentation("ACTIVA", "PUBLISHED"),
                strategy.present(CampaignStatus.PUBLISHED));
        assertEquals(new DefaultCampaignStatusPresentationStrategy.Presentation("EXITOSA", "SUCCESSFUL"),
                strategy.present(CampaignStatus.SUCCESSFUL));
        assertEquals(new DefaultCampaignStatusPresentationStrategy.Presentation("FALLIDA", "FAILED"),
                strategy.present(CampaignStatus.FAILED));
        assertEquals(new DefaultCampaignStatusPresentationStrategy.Presentation("BORRADOR", "DRAFT"),
                strategy.present(CampaignStatus.DRAFT));
        assertEquals(new DefaultCampaignStatusPresentationStrategy.Presentation("CANCELADA", "CANCELLED"),
                strategy.present(CampaignStatus.CANCELLED));
    }
}
