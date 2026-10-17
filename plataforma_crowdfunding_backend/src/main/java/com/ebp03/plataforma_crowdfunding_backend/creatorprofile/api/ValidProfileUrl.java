package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ProfileUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidProfileUrl {
    String message() default "Ingresa una URL válida.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
