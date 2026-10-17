package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import org.springframework.stereotype.Component;

@Component
public class DefaultCampaignStatusPresentationStrategy implements CampaignStatusPresentationStrategy {
    @Override
    public Presentation present(CampaignStatus status) {
        return switch (status) {
            case ACTIVE, PUBLISHED -> new Presentation("ACTIVA", status.name());
            case SUCCESSFUL -> new Presentation("EXITOSA", status.name());
            case FAILED -> new Presentation("FALLIDA", status.name());
            case DRAFT -> new Presentation("BORRADOR", status.name());
            case CANCELLED -> new Presentation("CANCELADA", status.name());
        };
    }
}
