package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignService;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignUpdateValidationException;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.ApiError;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CampaignControllerAdvice {
    @ExceptionHandler(CampaignService.FieldValidationException.class)
    public ResponseEntity<Object> handleFieldValidation(CampaignService.FieldValidationException exception) {
        if (exception.getErrors().isEmpty()) {
            return ResponseEntity.badRequest().body(new CampaignService.FieldError("", "INVALID"));
        }
        return ResponseEntity.badRequest().body(exception.getErrors().getFirst());
    }

    @ExceptionHandler(CampaignUpdateValidationException.class)
    public ResponseEntity<ApiError> handleCampaignUpdateValidation(CampaignUpdateValidationException exception) {
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_ERROR", "Revisa los campos indicados.", exception.getDetails()));
    }

    @ExceptionHandler(CampaignUpdatePublicationRequest.UnknownCampaignUpdateFieldException.class)
    public ResponseEntity<ApiError> handleUnknownCampaignUpdateField(
            CampaignUpdatePublicationRequest.UnknownCampaignUpdateFieldException exception) {
        String field = exception.getField();
        if (field.startsWith("media.")) {
            field = field.replaceFirst("^media\\.", "media[0].");
        }
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR",
                "Revisa los campos indicados.",
                List.of(new ValidationErrorDetail(field, "UNKNOWN_FIELD", "El campo no está permitido."))));
    }
}
