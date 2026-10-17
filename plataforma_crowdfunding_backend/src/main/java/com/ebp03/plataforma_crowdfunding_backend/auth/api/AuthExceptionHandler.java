package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.EmailAlreadyInUseException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidCredentialsException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidEmailException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidOAuthCodeException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidRoleException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.WeakPasswordException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidPasswordResetTokenException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.SamePasswordException;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.UpdateUserProfileRequest.UnknownProfileFieldException;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.UpdateUserProfileRequest.InvalidProfileFieldTypeException;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.SaveCreatorProfileRequest;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service.CreatorProfileValidationException;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.SaveCreatorProfileRequest.UnknownCreatorProfileFieldException;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignUpdatePublicationRequest.UnknownCampaignUpdateFieldException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidPathParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "INVALID_PARAMETER",
                "El parámetro " + exception.getName() + " debe ser un UUID válido."));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ResponseEntity<ApiError> emailAlreadyInUse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("EMAIL_ALREADY_IN_USE", "El correo ya está en uso."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicateConstraint(DataIntegrityViolationException exception) {
        if (isEmailConstraint(exception)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("EMAIL_ALREADY_IN_USE", "El correo ya está en uso."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DATA_CONFLICT", "La operación entra en conflicto con datos existentes."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("INVALID_CREDENTIALS", "Las credenciales no son válidas."));
    }

    @ExceptionHandler(InvalidEmailException.class)
    ResponseEntity<ApiError> invalidEmail() {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "El correo no es válido."));
    }

    @ExceptionHandler(WeakPasswordException.class)
    ResponseEntity<ApiError> weakPassword(WeakPasswordException exception) {
        return ResponseEntity.badRequest().body(new ApiError("WEAK_PASSWORD", "La contraseña no cumple los requisitos.", exception.getRequirements()));
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    ResponseEntity<ApiError> invalidPasswordResetToken() {
        return ResponseEntity.badRequest().body(new ApiError("RESET_TOKEN_INVALID", "El token de recuperación no es válido."));
    }

    @ExceptionHandler(SamePasswordException.class)
    ResponseEntity<ApiError> samePassword() {
        return ResponseEntity.badRequest().body(new ApiError("PASSWORD_REUSE", "La nueva contraseña debe ser diferente."));
    }

    @ExceptionHandler(InvalidRoleException.class)
    ResponseEntity<ApiError> invalidRole() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_ROLE", "El rol debe ser creator o sponsor."));
    }

    @ExceptionHandler(InvalidOAuthCodeException.class)
    ResponseEntity<ApiError> invalidOAuthCode() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_OAUTH_CODE", "El código OAuth no es válido."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        List<ValidationErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationErrorDetail(
                        profileField(exception, error.getField()),
                        "url".equals(error.getField().substring(error.getField().lastIndexOf('.') + 1))
                                && "ValidProfileUrl".equals(error.getCode())
                                ? "INVALID_URL" : validationCode(error.getCode()),
                        error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Revisa los campos indicados.", details));
    }

    @ExceptionHandler(CreatorProfileValidationException.class)
    ResponseEntity<ApiError> creatorProfileValidation(CreatorProfileValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "Revisa los campos indicados.", exception.getDetails()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest(HttpMessageNotReadableException exception) {
        UnknownCampaignUpdateFieldException unknownUpdateField = findUnknownCampaignUpdateField(exception);
        UnknownCreatorProfileFieldException unknownCreatorField = findUnknownCreatorProfileField(exception);
        UnknownProfileFieldException unknownField = findUnknownProfileField(exception);
        InvalidProfileFieldTypeException invalidType = findInvalidProfileFieldType(exception);
        List<ValidationErrorDetail> details;
        if (unknownUpdateField != null) {
            details = List.of(new ValidationErrorDetail(
                    unknownUpdateField.getField(), "UNKNOWN_FIELD", "El campo no está permitido."));
        } else if (unknownCreatorField != null) {
            details = List.of(new ValidationErrorDetail(
                    "profile." + unknownCreatorField.getField(), "UNKNOWN_FIELD", "El campo no está permitido."));
        } else if (unknownField != null) {
            details = List.of(new ValidationErrorDetail(unknownField.getField(), "UNKNOWN_FIELD", "El campo no está permitido."));
        } else if (invalidType != null) {
            details = List.of(new ValidationErrorDetail(invalidType.getField(), "INVALID_FORMAT", "El nombre debe ser texto."));
        } else {
            details = List.of(new ValidationErrorDetail("body", "INVALID_BODY", "El cuerpo de la solicitud no es válido."));
        }
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Revisa los campos indicados.", details));
    }

    private String profileField(MethodArgumentNotValidException exception, String field) {
        if (exception.getParameter().getParameterType() != SaveCreatorProfileRequest.class) return field;
        return field.startsWith("links[") ? field : "profile." + field;
    }

    private UnknownCreatorProfileFieldException findUnknownCreatorProfileField(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownCreatorProfileFieldException unknownField) return unknownField;
        }
        return null;
    }

    private UnknownCampaignUpdateFieldException findUnknownCampaignUpdateField(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownCampaignUpdateFieldException unknownField) return unknownField;
        }
        return null;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> statusError(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "La solicitud no es válida." : exception.getReason();
        String code = reason.matches("[A-Z][A-Z0-9_]+") ? reason : "REQUEST_ERROR";
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(code, code.equals("REQUEST_ERROR") ? reason : "La operación no pudo completarse."));
    }

    private String validationCode(String constraintCode) {
        return switch (constraintCode == null ? "" : constraintCode) {
            case "NotBlank", "NotNull" -> "REQUIRED";
            case "Size" -> "LENGTH";
            case "Pattern" -> "INVALID_FORMAT";
            default -> "INVALID";
        };
    }

    private UnknownProfileFieldException findUnknownProfileField(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownProfileFieldException unknownField) return unknownField;
        }
        return null;
    }

    private InvalidProfileFieldTypeException findInvalidProfileFieldType(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidProfileFieldTypeException invalidType) return invalidType;
        }
        return null;
    }

    private boolean isEmailConstraint(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                return constraint != null && constraint.toLowerCase(java.util.Locale.ROOT).contains("users_email");
            }
        }
        return false;
    }
}
