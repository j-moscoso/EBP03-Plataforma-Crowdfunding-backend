package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.util.Locale;

public class ExternalMediaUrlValidator implements ConstraintValidator<ValidExternalMediaUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute()
                    && "https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
