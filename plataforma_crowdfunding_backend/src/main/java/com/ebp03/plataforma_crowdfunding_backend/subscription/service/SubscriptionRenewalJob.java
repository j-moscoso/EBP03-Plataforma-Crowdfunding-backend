package com.ebp03.plataforma_crowdfunding_backend.subscription.service;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionRenewalJob {
    private final SubscriptionService subscriptionService;

    public SubscriptionRenewalJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(
            fixedDelayString = "${app.subscription.renewal-fixed-delay-ms:60000}",
            initialDelayString = "${app.subscription.renewal-initial-delay-ms:60000}")
    public void processDueSubscriptions() {
        subscriptionService.processDueSubscriptions(Instant.now());
    }
}
