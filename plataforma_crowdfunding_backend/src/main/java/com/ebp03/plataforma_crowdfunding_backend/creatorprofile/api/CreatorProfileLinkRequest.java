package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatorProfileLinkRequest(
        @NotBlank(message = "El tipo de enlace es obligatorio.")
        @Pattern(regexp = "PORTFOLIO|PROFESSIONAL_NETWORK|OTHER_PROJECT",
                message = "El tipo de enlace no está permitido.")
        String linkType,

        @Size(max = 120, message = "La etiqueta no puede superar 120 caracteres.")
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]*", message = "La etiqueta contiene caracteres no permitidos.")
        String label,

        @jakarta.validation.constraints.NotBlank(message = "La URL es obligatoria.")
        @Size(max = 1000, message = "La URL no puede superar 1000 caracteres.")
        @ValidProfileUrl
        String url,

        @Min(value = 0, message = "El orden debe ser un entero no negativo.")
        Integer sortOrder) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new SaveCreatorProfileRequest.UnknownCreatorProfileFieldException("links." + field);
    }
}
