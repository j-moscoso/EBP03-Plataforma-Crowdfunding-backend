package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.PasswordResetResponses;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.*;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.*;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final String GENERIC_MESSAGE = "Si la cuenta cumple los requisitos, recibirás instrucciones para restablecer tu contraseña.";
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetRateLimitRepository rateLimitRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordResetNotificationSender notificationSender;
    private final int tokenMinutes;
    private final int windowMinutes;
    private final int ipLimit;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                PasswordResetRateLimitRepository rateLimitRepository, AuditLogRepository auditLogRepository,
                                NotificationRepository notificationRepository, SessionRepository sessionRepository,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                PasswordResetNotificationSender notificationSender,
                                @Value("${app.auth.password-reset-token-minutes:15}") int tokenMinutes,
                                @Value("${app.auth.password-reset-window-minutes:15}") int windowMinutes,
                                @Value("${app.auth.password-reset-ip-limit:5}") int ipLimit) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.rateLimitRepository = rateLimitRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.notificationSender = notificationSender;
        this.tokenMinutes = tokenMinutes;
        this.windowMinutes = windowMinutes;
        this.ipLimit = ipLimit;
    }

    @Transactional
    public PasswordResetResponses.GenericMessage request(String rawEmail, String ipAddress, String userAgent) {
        String ip = safeIp(ipAddress);
        Instant now = Instant.now();
        PasswordResetRateLimit ipLimitRecord = limitFor(null, ip, "IP:" + ip, now);
        ipLimitRecord.registerFailure(now, ipLimit, now.plus(Duration.ofMinutes(windowMinutes)));
        rateLimitRepository.save(ipLimitRecord);
        if (isBlocked(ipLimitRecord, now)) {
            audit(null, null, "PASSWORD_RESET_BLOCKED", "BLOCKED", ip, userAgent, "{\"scope\":\"ip\"}");
            return new PasswordResetResponses.GenericMessage(GENERIC_MESSAGE);
        }
        User user = null;
        try {
            String email = AuthService.normalizeEmail(rawEmail == null ? "" : rawEmail);
            user = userRepository.findByEmailForUpdate(email).orElse(null);
        } catch (InvalidEmailException ignored) {
            // The public contract intentionally does not distinguish malformed or unknown accounts.
        }
        if (user != null && user.getStatus() == AccountStatus.ACTIVE) {
            PasswordResetRateLimit accountLimit = limitFor(user.getId(), ip, accountKey(user.getId(), ip), now);
            if (!isBlocked(accountLimit, now)) {
                tokenRepository.invalidateUnusedForUser(user.getId(), now);
                String rawToken = generateToken();
                Instant expiresAt = now.plus(Duration.ofMinutes(tokenMinutes));
                tokenRepository.save(new PasswordResetToken(user, hash(rawToken), expiresAt, ip));
                notificationSender.send(user.getEmail(), rawToken, expiresAt);
                audit(user.getId(), user.getId(), "PASSWORD_RESET_REQUESTED", "SUCCESS", ip, userAgent, null);
            } else {
                audit(user.getId(), user.getId(), "PASSWORD_RESET_BLOCKED", "BLOCKED", ip, userAgent, "{\"scope\":\"account\"}");
            }
        } else {
            audit(null, null, "PASSWORD_RESET_REQUESTED", "IGNORED", ip, userAgent, null);
        }
        return new PasswordResetResponses.GenericMessage(GENERIC_MESSAGE);
    }

    @Transactional
    public PasswordResetResponses.Validation validate(String rawToken, String ipAddress, String userAgent) {
        PasswordResetToken token = findUsableOrRecordFailure(rawToken, ipAddress, userAgent);
        return new PasswordResetResponses.Validation(true, token.getExpiresAt());
    }

    @Transactional
    public PasswordResetResponses.Confirmation confirm(String rawToken, String newPassword,
                                                        String ipAddress, String userAgent) {
        PasswordResetToken token = findUsableOrRecordFailure(rawToken, ipAddress, userAgent);
        List<String> missing = passwordPolicy.missingRequirements(newPassword);
        if (!missing.isEmpty()) throw new WeakPasswordException(missing);
        User user = token.getUser();
        if (user.getPasswordHash() != null && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new SamePasswordException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed();
        sessionRepository.revokeAllForUser(user.getId());
        audit(user.getId(), user.getId(), "PASSWORD_CHANGED", "SUCCESS", safeIp(ipAddress), userAgent, null);
        notificationRepository.save(new Notification(user.getId(), "PASSWORD_CHANGED",
                "Cambio de contraseña", "Tu contraseña fue actualizada recientemente."));
        return new PasswordResetResponses.Confirmation("Contraseña actualizada correctamente.", true);
    }

    private PasswordResetToken findUsableOrRecordFailure(String rawToken, String ipAddress, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidPasswordResetTokenException();
        PasswordResetToken token = tokenRepository.findForUpdate(hash(rawToken)).orElse(null);
        Instant now = Instant.now();
        if (token == null || !token.isUsable(now)) {
            if (token != null) {
                token.incrementAttempts();
                registerFailure(token.getUser().getId(), safeIp(ipAddress), now, userAgent);
            }
            throw new InvalidPasswordResetTokenException();
        }
        PasswordResetRateLimit limit = limitFor(token.getUser().getId(), safeIp(ipAddress),
                accountKey(token.getUser().getId(), safeIp(ipAddress)), now);
        if (isBlocked(limit, now)) throw new InvalidPasswordResetTokenException();
        return token;
    }

    private void registerFailure(UUID userId, String ip, Instant now, String userAgent) {
        PasswordResetRateLimit account = limitFor(userId, ip, accountKey(userId, ip), now);
        account.registerFailure(now, 3, now.plus(Duration.ofMinutes(windowMinutes)));
        rateLimitRepository.save(account);
        audit(userId, userId, account.isBlocked(now) ? "PASSWORD_RESET_BLOCKED" : "PASSWORD_RESET_FAILED",
                "FAILURE", ip, userAgent, "{\"scope\":\"account\"}");
    }

    private PasswordResetRateLimit limitFor(UUID userId, String ip, String key, Instant now) {
        PasswordResetRateLimit record = rateLimitRepository.findForUpdate(key).orElseGet(
                () -> new PasswordResetRateLimit(userId, ip, key, now));
        if (Duration.between(record.getWindowStartedAt(), now).toMinutes() >= windowMinutes) record.resetWindow(now);
        record.registerEvent(now);
        try {
            return rateLimitRepository.save(record);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("No se pudo registrar el límite de recuperación de contraseña", exception);
        }
    }

    private boolean isBlocked(PasswordResetRateLimit record, Instant now) {
        return record.isBlocked(now);
    }

    private void audit(UUID actor, UUID target, String event, String outcome, String ip, String userAgent, String metadata) {
        auditLogRepository.save(new AuditLog(actor, target, event, outcome, ip, userAgent, metadata));
    }

    private String accountKey(UUID userId, String ip) { return "USER:" + userId + ":IP:" + ip; }
    private String safeIp(String ip) { return ip == null || ip.isBlank() ? "unknown" : ip.substring(0, Math.min(ip.length(), 64)); }
    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
