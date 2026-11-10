package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Session;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.SessionService;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.SupportedCampaignsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sponsors/me")
public class SupportedCampaignsController {
    private static final Set<String> ALLOWED_PARAMETERS = Set.of("page", "pageSize");

    private final SupportedCampaignsService supportedCampaignsService;
    private final SessionService sessionService;
    private final String sessionCookieName;

    public SupportedCampaignsController(
            SupportedCampaignsService supportedCampaignsService,
            SessionService sessionService,
            @Value("${app.auth.session-cookie-name:AUTH_SESSION}") String sessionCookieName) {
        this.supportedCampaignsService = supportedCampaignsService;
        this.sessionService = sessionService;
        this.sessionCookieName = sessionCookieName;
    }

    @GetMapping("/supported-campaigns")
    public ResponseEntity<SupportedCampaignsService.SupportedCampaignsPageResponse> getSupportedCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        if (!ALLOWED_PARAMETERS.containsAll(request.getParameterMap().keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        Session session = readSession(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        User sponsor = session.getUser();
        return ResponseEntity.ok(supportedCampaignsService.getSupportedCampaigns(sponsor, page, pageSize));
    }

    private Optional<Session> readSession(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (sessionCookieName.equals(cookie.getName())) {
                return sessionService.findUsable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
