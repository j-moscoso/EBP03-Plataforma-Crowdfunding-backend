package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import java.math.BigDecimal;

public interface PaymentProvider {
    Result charge(String paymentMethodId, BigDecimal amount, String currency);

    record Result(boolean successful, String providerPaymentId, String failureCode) { }
}