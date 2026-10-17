package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.util.Locale;

public class ProfileUrlValidator implements ConstraintValidator<ValidProfileUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && scheme != null
                    && (scheme.toLowerCase(Locale.ROOT).equals("http")
                    || scheme.toLowerCase(Locale.ROOT).equals("https"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
