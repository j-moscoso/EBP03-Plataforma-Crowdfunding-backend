package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.SupportedCampaignProjection;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.SupportedCampaignCurrencyProjection;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportedCampaignsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupportedCampaignsService.class);
    private static final String EMPTY_MESSAGE = "Aún no has apoyado ningún proyecto";
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100.00");

    private final ContributionRepository contributions;
    private final CampaignStatusPresentationStrategy statusPresentation;

    public SupportedCampaignsService(
            ContributionRepository contributions,
            CampaignStatusPresentationStrategy statusPresentation) {
        this.contributions = contributions;
        this.statusPresentation = statusPresentation;
    }

    @Transactional(readOnly = true)
    public SupportedCampaignsPageResponse getSupportedCampaigns(User authenticatedUser, int page, int pageSize) {
        requireSponsor(authenticatedUser);
        validatePagination(page, pageSize);

        long mixedCurrencyCampaignCount =
                contributions.countMixedCurrencyCampaignsForSponsor(authenticatedUser.getId());
        if (mixedCurrencyCampaignCount > 0) {
            LOGGER.warn("Omitiendo {} campaña(s) con monedas confirmadas incompatibles para el sponsor {}",
                    mixedCurrencyCampaignCount, authenticatedUser.getId());
        }

        Page<SupportedCampaignProjection> result = contributions.findSupportedCampaigns(
                authenticatedUser.getId(), PageRequest.of(page, pageSize));
        List<OmittedCampaignResponse> omittedCampaigns = findOmittedCampaigns(authenticatedUser.getId());
        List<WarningResponse> warnings = omittedCampaigns.isEmpty()
                ? List.of()
                : List.of(new WarningResponse(
                        "SUPPORTED_CAMPAIGNS_OMITTED",
                        "Algunas campañas no se muestran porque tienen monedas confirmadas incompatibles.",
                        omittedCampaigns.size()));
        List<SupportedCampaignResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new SupportedCampaignsPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getTotalElements() == 0 && omittedCampaigns.isEmpty() ? EMPTY_MESSAGE : null,
                omittedCampaigns,
                warnings);
    }

    private List<OmittedCampaignResponse> findOmittedCampaigns(UUID sponsorId) {
        Map<UUID, OmittedCampaignBuilder> grouped = new LinkedHashMap<>();
        for (SupportedCampaignCurrencyProjection row : contributions.findSupportedCampaignCurrencies(sponsorId)) {
            UUID campaignId = UUID.fromString(row.getCampaignId());
            grouped.computeIfAbsent(campaignId, ignored -> new OmittedCampaignBuilder(row.getTitle()))
                    .currencies.add(row.getCurrency().toUpperCase(java.util.Locale.ROOT));
        }
        List<OmittedCampaignResponse> omitted = new ArrayList<>();
        for (Map.Entry<UUID, OmittedCampaignBuilder> entry : grouped.entrySet()) {
            if (entry.getValue().currencies.size() > 1) {
                omitted.add(new OmittedCampaignResponse(
                        entry.getKey(),
                        entry.getValue().title,
                        "MIXED_CONFIRMED_CURRENCIES",
                        List.copyOf(entry.getValue().currencies)));
            }
        }
        return omitted;
    }

    private SupportedCampaignResponse toResponse(SupportedCampaignProjection projection) {
        if (projection.getGoalAmount().signum() <= 0) {
            throw new IllegalStateException("Campaign goal amount must be positive");
        }
        BigDecimal percentage = projection.getRaisedAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(projection.getGoalAmount(), 2, RoundingMode.HALF_UP)
                .min(MAX_PERCENTAGE);
        CampaignStatusPresentationStrategy.Presentation presentation =
                statusPresentation.present(CampaignStatus.valueOf(projection.getStatusCode()));
        return new SupportedCampaignResponse(
                UUID.fromString(projection.getCampaignId()),
                projection.getImageUrl(),
                projection.getTitle(),
                projection.getAmountContributedByMe(),
                projection.getCurrency(),
                projection.getRaisedAmount(),
                projection.getGoalAmount(),
                percentage,
                presentation.status(),
                presentation.statusCode(),
                projection.getLastContributionAt().toInstant());
    }

    private void requireSponsor(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (user.getRole() != UserRole.SPONSOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SPONSOR_ROLE_REQUIRED");
        }
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
    }

    public record SupportedCampaignResponse(
            UUID campaignId,
            String imageUrl,
            String title,
            BigDecimal amountContributedByMe,
            String currency,
            BigDecimal raisedAmount,
            BigDecimal goalAmount,
            BigDecimal totalRaisedPercentage,
            String status,
            String statusCode,
            Instant lastContributionAt) {
    }

    public record SupportedCampaignsPageResponse(
            List<SupportedCampaignResponse> content,
            int page,
            int pageSize,
            long totalElements,
            int totalPages,
            @JsonInclude(JsonInclude.Include.NON_NULL) String emptyMessage,
            List<OmittedCampaignResponse> omittedCampaigns,
            List<WarningResponse> warnings) {
    }

    public record OmittedCampaignResponse(
            UUID campaignId,
            String title,
            String reasonCode,
            List<String> currencies) {
    }

    public record WarningResponse(
            String code,
            String message,
            int affectedCampaignCount) {
    }

    private static final class OmittedCampaignBuilder {
        private final String title;
        private final TreeSet<String> currencies = new TreeSet<>();

        private OmittedCampaignBuilder(String title) {
            this.title = title;
        }
    }
}
