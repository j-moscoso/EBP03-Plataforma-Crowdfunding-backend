package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.SupportedCampaignsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public SupportedCampaignsController(SupportedCampaignsService supportedCampaignsService) {
        this.supportedCampaignsService = supportedCampaignsService;
    }

    @GetMapping("/supported-campaigns")
    public ResponseEntity<SupportedCampaignsService.SupportedCampaignsPageResponse> getSupportedCampaigns(
            @AuthenticationPrincipal User sponsor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        if (!ALLOWED_PARAMETERS.containsAll(request.getParameterMap().keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        return ResponseEntity.ok(supportedCampaignsService.getSupportedCampaigns(sponsor, page, pageSize));
    }
}
