package com.ebp03.plataforma_crowdfunding_backend.auth;

import com.ebp03.plataforma_crowdfunding_backend.auth.service.PasswordResetNotificationSender;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("test")
@Primary
@Component
public class FakePasswordResetNotificationSender implements PasswordResetNotificationSender {
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    @Override
    public void send(String email, String token, Instant expiresAt) {
        tokens.put(email, token);
    }

    public String tokenFor(String email) { return tokens.get(email); }
}
