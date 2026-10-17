package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ExternalMediaUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidExternalMediaUrl {
    String message() default "La URL debe ser absoluta y usar HTTPS.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
