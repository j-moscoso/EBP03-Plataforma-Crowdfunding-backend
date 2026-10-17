package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentWebhookAdapter {
    public PaymentStatus toPaymentStatus(String eventType, String externalStatus) {
        if (eventType == null || eventType.isBlank() || eventType.length() > 80
                || externalStatus == null || externalStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        PaymentStatus status = switch (externalStatus.trim().toLowerCase(Locale.ROOT)) {
            case "processing", "pending" -> PaymentStatus.PROCESSING;
            case "succeeded", "successful", "success" -> PaymentStatus.SUCCEEDED;
            case "failed", "failure" -> PaymentStatus.FAILED;
            case "refunded", "refund" -> PaymentStatus.REFUNDED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        };
        String normalizedEvent = eventType.trim().toLowerCase(Locale.ROOT);
        PaymentStatus eventStatus = eventStatus(normalizedEvent);
        if (eventStatus != null && eventStatus != status) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        return status;
    }

    private PaymentStatus eventStatus(String eventType) {
        String eventName = eventType.substring(eventType.lastIndexOf('.') + 1);
        return switch (eventName) {
            case "processing", "pending" -> PaymentStatus.PROCESSING;
            case "succeeded", "successful", "success" -> PaymentStatus.SUCCEEDED;
            case "failed", "failure", "payment_failed" -> PaymentStatus.FAILED;
            case "refunded", "refund" -> PaymentStatus.REFUNDED;
            default -> null;
        };
    }
}
