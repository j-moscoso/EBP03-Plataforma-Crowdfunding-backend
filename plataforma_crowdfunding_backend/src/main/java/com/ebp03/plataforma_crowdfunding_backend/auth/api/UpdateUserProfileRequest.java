package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UpdateUserProfileRequest {
    @NotBlank(message = "El nombre es obligatorio.")
    @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres.")
    @Pattern(regexp = "[\\p{L}\\p{M} .,'’\\-]+", message = "El nombre contiene caracteres no permitidos.")
    private String name;

    @JsonProperty("name")
    public void setName(Object value) {
        if (value == null) {
            name = null;
        } else if (value instanceof String text) {
            name = text.strip();
        } else {
            throw new InvalidProfileFieldTypeException("name");
        }
    }

    public String getName() {
        return name;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new UnknownProfileFieldException(field);
    }

    public static final class UnknownProfileFieldException extends IllegalArgumentException {
        private final String field;

        public UnknownProfileFieldException(String field) {
            super("Unsupported profile field");
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    public static final class InvalidProfileFieldTypeException extends IllegalArgumentException {
        private final String field;

        public InvalidProfileFieldTypeException(String field) {
            super("Invalid profile field type");
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }
}
