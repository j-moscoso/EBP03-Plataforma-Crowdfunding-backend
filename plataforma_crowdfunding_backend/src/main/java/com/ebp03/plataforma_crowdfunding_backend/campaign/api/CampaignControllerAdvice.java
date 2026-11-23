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
    @ExceptionHandler(com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignDiscussionService.DiscussionFieldValidationException.class)
    public ResponseEntity<ApiError> handleDiscussionValidation(
            com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignDiscussionService.DiscussionFieldValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "Revisa los campos indicados.", exception.getDetails()));
    }

    @ExceptionHandler(CampaignDiscussionRequest.UnknownDiscussionFieldException.class)
    public ResponseEntity<ApiError> handleUnknownDiscussionField(
            CampaignDiscussionRequest.UnknownDiscussionFieldException exception) {
        return unknownDiscussionField(exception.getField());
    }

    @ExceptionHandler(CampaignFaqRequest.UnknownDiscussionFieldException.class)
    public ResponseEntity<ApiError> handleUnknownFaqField(
            CampaignFaqRequest.UnknownDiscussionFieldException exception) {
        return unknownDiscussionField(exception.getField());
    }

    private ResponseEntity<ApiError> unknownDiscussionField(String field) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "Revisa los campos indicados.",
                List.of(new ValidationErrorDetail(field, "UNKNOWN_FIELD", "El campo no está permitido."))));
    }
    @ExceptionHandler(CampaignService.FieldValidationException.class)
    public ResponseEntity<ApiError> handleFieldValidation(CampaignService.FieldValidationException exception) {
        List<ValidationErrorDetail> details = exception.getErrors().stream()
                .map(error -> new ValidationErrorDetail(error.field(), error.code(), error.message()))
                .toList();
        return ResponseEntity.badRequest().body(
                new ApiError("VALIDATION_ERROR", "Revisa los campos indicados.", details));
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
