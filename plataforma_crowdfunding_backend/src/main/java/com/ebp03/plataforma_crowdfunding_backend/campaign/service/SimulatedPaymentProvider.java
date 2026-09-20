package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class SimulatedPaymentProvider implements PaymentProvider {
    @Override
    public Result charge(String paymentMethodId, BigDecimal amount, String currency) {
        if ("failure".equalsIgnoreCase(paymentMethodId) || "sim_failure".equalsIgnoreCase(paymentMethodId)) {
            return new Result(false, null, "SIMULATED_FAILURE");
        }
        return new Result(true, "sim_" + UUID.randomUUID(), null);
    }
}