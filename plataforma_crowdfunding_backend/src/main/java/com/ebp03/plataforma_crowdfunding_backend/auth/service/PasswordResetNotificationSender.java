package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import java.time.Instant;

public interface PasswordResetNotificationSender {
    void send(String email, String token, Instant expiresAt);
}
