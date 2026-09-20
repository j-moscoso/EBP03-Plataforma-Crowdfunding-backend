package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Session;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.SessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final SessionRepository sessionRepository;
    private final int sessionDays;
    private final int rememberDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(SessionRepository sessionRepository,
                          @Value("${app.auth.session-days:7}") int sessionDays,
                          @Value("${app.auth.remember-days:30}") int rememberDays) {
        this.sessionRepository = sessionRepository;
        this.sessionDays = sessionDays;
        this.rememberDays = rememberDays;
    }

    @Transactional
    public IssuedSession create(User user, boolean rememberMe, String userAgent, String ipAddress) {
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(rememberMe ? rememberDays : sessionDays));
        Session session = sessionRepository.save(new Session(user, hash(rawToken), expiresAt, userAgent, ipAddress));
        return new IssuedSession(rawToken, session);
    }

    @Transactional(readOnly = true)
    public Optional<Session> findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return sessionRepository.findByTokenHash(hash(rawToken)).filter(session -> session.isUsable(Instant.now()));
    }

    @Transactional
    public void revoke(String rawToken) {
        findByToken(rawToken).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.revoke();
                sessionRepository.save(session);
            }
        });
    }

    public String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Optional<Session> findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return sessionRepository.findByTokenHash(hash(rawToken));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedSession(String rawToken, Session session) { }
}
