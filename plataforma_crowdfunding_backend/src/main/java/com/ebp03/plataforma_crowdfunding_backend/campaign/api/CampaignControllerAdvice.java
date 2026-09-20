package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignService;
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
}
