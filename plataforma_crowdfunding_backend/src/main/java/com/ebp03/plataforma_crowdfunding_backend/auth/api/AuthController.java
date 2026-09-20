package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Session;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.security.SessionAuthenticationFilter;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.AuthService;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(AuthService authService, SessionService sessionService,
                          @Value("${app.auth.session-cookie-name:AUTH_SESSION}") String cookieName,
                          @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
                          @Value("${app.auth.cookie-same-site:Lax}") String cookieSameSite) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @GetMapping({"/register", "/login", "/social"})
    public EndpointInfo endpointInfo(HttpServletRequest request) {
        return new EndpointInfo(request.getRequestURI(), "POST", "Este endpoint recibe un cuerpo JSON. Use POST para ejecutar la operación.");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.AuthenticatedUser authenticated = authService.register(request, userAgent(httpRequest), httpRequest.getRemoteAddr());
        addSessionCookie(response, authenticated.session());
        return ResponseEntity.status(201).body(toResponse(authenticated));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.AuthenticatedUser authenticated = authService.login(request, userAgent(httpRequest), httpRequest.getRemoteAddr());
        addSessionCookie(response, authenticated.session());
        return ResponseEntity.ok(toResponse(authenticated));
    }

    @PostMapping("/social")
    public ResponseEntity<AuthResponse> social(@Valid @RequestBody SocialLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.AuthenticatedUser authenticated = authService.socialLogin(request, userAgent(httpRequest), httpRequest.getRemoteAddr());
        addSessionCookie(response, authenticated.session());
        return ResponseEntity.status(201).body(toResponse(authenticated));
    }

    @GetMapping("/me")
    public PublicUser me(@AuthenticationPrincipal User user) { return PublicUser.from(user); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookie(request).ifPresent(sessionService::revoke);
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie().toString());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse toResponse(AuthService.AuthenticatedUser authenticated) {
        Session session = authenticated.session().session();
        return new AuthResponse(PublicUser.from(authenticated.user()), new AuthResponse.SessionResponse(session.getExpiresAt()));
    }

    private void addSessionCookie(HttpServletResponse response, SessionService.IssuedSession issued) {
        long maxAge = Math.max(1, Duration.between(Instant.now(), issued.session().getExpiresAt()).toSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName, issued.rawToken())
                .httpOnly(true).secure(cookieSecure).path("/").sameSite(cookieSameSite).maxAge(maxAge).build().toString());
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(cookieName, "").httpOnly(true).secure(cookieSecure).path("/").sameSite(cookieSameSite).maxAge(0).build();
    }

    private java.util.Optional<String> sessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return java.util.Optional.empty();
        for (Cookie cookie : request.getCookies()) if (cookieName.equals(cookie.getName())) return java.util.Optional.of(cookie.getValue());
        return java.util.Optional.empty();
    }

    private String userAgent(HttpServletRequest request) { return request.getHeader("User-Agent"); }

    public record EndpointInfo(String endpoint, String method, String message) { }

}
