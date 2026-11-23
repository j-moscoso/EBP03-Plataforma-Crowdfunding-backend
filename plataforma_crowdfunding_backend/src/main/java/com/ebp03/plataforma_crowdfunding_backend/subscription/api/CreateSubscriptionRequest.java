package com.ebp03.plataforma_crowdfunding_backend.subscription.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotNull UUID creatorId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2)
        BigDecimal amount,
        @NotBlank String currency,
        @NotBlank @Size(max = 160) String paymentMethodId) {
}
