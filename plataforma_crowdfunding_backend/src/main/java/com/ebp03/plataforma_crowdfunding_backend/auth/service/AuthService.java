package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.AuthResponse;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.LoginRequest;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.RegisterRequest;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.SocialLoginRequest;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.SocialAccount;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.SocialAccountRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.EmailAlreadyInUseException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidCredentialsException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidEmailException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.InvalidRoleException;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthExceptions.WeakPasswordException;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OAuthProviderClient oauthProviderClient;

    public AuthService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
                       SessionService sessionService, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, OAuthProviderClient oauthProviderClient) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.oauthProviderClient = oauthProviderClient;
    }

    @Transactional
    public AuthenticatedUser register(RegisterRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) throw new EmailAlreadyInUseException();
        var missing = passwordPolicy.missingRequirements(request.password());
        if (!missing.isEmpty()) throw new WeakPasswordException(missing);
        User user = userRepository.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
                parseRole(request.role()), VerificationStatus.PENDING, AccountStatus.ACTIVE));
        return issue(user, false, userAgent, ipAddress);
    }

    @Transactional
    public AuthenticatedUser login(LoginRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        boolean valid = user != null && user.getPasswordHash() != null
                ? passwordEncoder.matches(request.password(), user.getPasswordHash())
                : passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
        if (!valid || user == null || user.getStatus() != AccountStatus.ACTIVE) throw new InvalidCredentialsException();
        return issue(user, Boolean.TRUE.equals(request.rememberMe()), userAgent, ipAddress);
    }

    @Transactional
    public AuthenticatedUser socialLogin(SocialLoginRequest request, String userAgent, String ipAddress) {
        OAuthProviderClient.OAuthIdentity identity = oauthProviderClient.exchangeCode(request.provider(), request.authorizationCode());
        String provider = request.provider().trim().toLowerCase(Locale.ROOT);
        User user = socialAccountRepository.findByProviderAndProviderUserId(provider, identity.providerUserId())
                .map(SocialAccount::getUser)
                .orElseGet(() -> createSocialUser(identity, provider, request.role()));
        return issue(user, true, userAgent, ipAddress);
    }

    private User createSocialUser(OAuthProviderClient.OAuthIdentity identity, String provider, String requestedRole) {
        String email = normalizeEmail(identity.email());
        User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.save(new User(
                identity.name().trim(), email, null, requestedRole == null ? UserRole.SPONSOR : parseRole(requestedRole),
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE)));
        socialAccountRepository.save(new SocialAccount(user, provider, identity.providerUserId(), email));
        return user;
    }

    private AuthenticatedUser issue(User user, boolean rememberMe, String userAgent, String ipAddress) {
        SessionService.IssuedSession issued = sessionService.create(user, rememberMe, userAgent, ipAddress);
        return new AuthenticatedUser(user, issued);
    }

    private UserRole parseRole(String role) {
        try { return UserRole.valueOf(role.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new InvalidRoleException(); }
    }

    public static String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new InvalidEmailException();
        return normalized;
    }

    public record AuthenticatedUser(User user, SessionService.IssuedSession session) { }
}
