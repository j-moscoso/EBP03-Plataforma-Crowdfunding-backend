package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class UnavailablePasswordResetNotificationSender implements PasswordResetNotificationSender {
    @Override
    public void send(String email, String token, Instant expiresAt) {
        throw new IllegalStateException("Configure un PasswordResetNotificationSender para entregar correos de recuperación");
    }
}
