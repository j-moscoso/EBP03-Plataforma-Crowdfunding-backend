package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import java.util.List;

public class CampaignUpdateValidationException extends RuntimeException {
    private final List<ValidationErrorDetail> details;

    public CampaignUpdateValidationException(List<ValidationErrorDetail> details) {
        super("Campaign update validation failed");
        this.details = List.copyOf(details);
    }

    public List<ValidationErrorDetail> getDetails() {
        return details;
    }
}
