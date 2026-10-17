package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import java.util.List;

public class CreatorProfileValidationException extends RuntimeException {
    private final List<ValidationErrorDetail> details;

    public CreatorProfileValidationException(List<ValidationErrorDetail> details) {
        super("Creator profile validation failed");
        this.details = List.copyOf(details);
    }

    public List<ValidationErrorDetail> getDetails() {
        return details;
    }
}
