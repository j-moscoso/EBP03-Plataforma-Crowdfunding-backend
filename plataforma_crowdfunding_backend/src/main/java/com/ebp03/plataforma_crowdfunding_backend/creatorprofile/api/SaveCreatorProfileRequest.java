package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveCreatorProfileRequest(
        @NotBlank(message = "El nombre visible es obligatorio.")
        @Size(min = 2, max = 120, message = "El nombre visible debe tener entre 2 y 120 caracteres.")
        @Pattern(regexp = "[\\p{L}\\p{M} .,'’\\-]+", message = "El nombre visible contiene caracteres no permitidos.")
        String displayName,

        @Size(max = 2000, message = "La biografía no puede superar 2000 caracteres.")
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]*", message = "La biografía contiene caracteres no permitidos.")
        String bio,

        @Size(max = 120, message = "El título profesional no puede superar 120 caracteres.")
        @Pattern(regexp = "[\\p{L}\\p{M}0-9 .,'’()&/\\-]*", message = "El título profesional contiene caracteres no permitidos.")
        String professionalTitle,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres.")
        @Pattern(regexp = "\\+573[0-9]{9}", message = "Ingresa un celular colombiano en formato +573XXXXXXXXX.")
        String phone,

        @NotBlank(message = "El tipo de cuenta es obligatorio.")
        @Pattern(regexp = "SAVINGS|CHECKING", message = "El tipo de cuenta debe ser SAVINGS o CHECKING.")
        String bankAccountType,

        @NotBlank(message = "El banco es obligatorio.")
        @Size(max = 120, message = "El banco no puede superar 120 caracteres.")
        @Pattern(regexp = "[\\p{L}\\p{M}0-9 .,'’&()\\-]+", message = "El banco contiene caracteres no permitidos.")
        String bankName,

        @NotBlank(message = "El número de cuenta es obligatorio.")
        @Pattern(regexp = "[0-9]{5,80}", message = "El número de cuenta debe contener entre 5 y 80 dígitos.")
        String bankAccountNumber,

        @NotBlank(message = "El titular de la cuenta es obligatorio.")
        @Size(max = 160, message = "El titular no puede superar 160 caracteres.")
        @Pattern(regexp = "[\\p{L}\\p{M} .,'’\\-]+", message = "El titular contiene caracteres no permitidos.")
        String bankAccountHolder,

        @NotBlank(message = "El documento es obligatorio.")
        @Pattern(regexp = "[0-9]{5,15}", message = "El documento colombiano debe contener entre 5 y 15 dígitos.")
        String bankAccountDocument,

        @NotNull(message = "La lista de enlaces es obligatoria; usa [] si no tienes enlaces.")
        @Size(max = 20, message = "No se permiten más de 20 enlaces.")
        List<@NotNull @Valid CreatorProfileLinkRequest> links) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new UnknownCreatorProfileFieldException(field);
    }

    public static final class UnknownCreatorProfileFieldException extends IllegalArgumentException {
        private final String field;

        public UnknownCreatorProfileFieldException(String field) {
            super("Unsupported creator profile field");
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }
}
