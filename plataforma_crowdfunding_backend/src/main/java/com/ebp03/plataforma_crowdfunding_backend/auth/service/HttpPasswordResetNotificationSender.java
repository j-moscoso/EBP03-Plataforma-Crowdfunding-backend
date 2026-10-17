package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Primary
@Component
@ConditionalOnProperty(name = "app.auth.password-reset-notification-enabled", havingValue = "true")
public class HttpPasswordResetNotificationSender implements PasswordResetNotificationSender {
    private final RestClient client;

    public HttpPasswordResetNotificationSender(
            @Value("${app.auth.password-reset-notification-endpoint}") String endpoint) {
        this.client = RestClient.create(endpoint);
    }

    @Override
    public void send(String email, String token, Instant expiresAt) {
        client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Delivery(email, token, expiresAt))
                .retrieve()
                .toBodilessEntity();
    }

    private record Delivery(String email, String token, Instant expiresAt) { }
}
