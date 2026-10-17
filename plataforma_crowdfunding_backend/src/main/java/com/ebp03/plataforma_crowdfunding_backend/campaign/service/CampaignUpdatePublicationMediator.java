package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignUpdateMediaRequest;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignUpdateMediaResponse;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignUpdatePublicationRequest;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignUpdatePublicationResponse;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignUpdateWallResponse;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdateMedia;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdateMediaType;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignUpdateMediaRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignUpdateRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignUpdatePublicationMediator {
    private static final Pattern FORBIDDEN_SCHEME =
            Pattern.compile("(?i)(javascript|data|vbscript)\\s*:");
    private static final Set<CampaignStatus> PUBLIC_WALL_STATUSES = Set.of(
            CampaignStatus.PUBLISHED, CampaignStatus.ACTIVE, CampaignStatus.SUCCESSFUL,
            CampaignStatus.FAILED, CampaignStatus.CANCELLED);
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignUpdateRepository updateRepository;
    private final CampaignUpdateMediaRepository mediaRepository;
    private final ContributionRepository contributionRepository;
    private final NotificationPublisher notificationPublisher;
    private final Validator validator;
    private final int maxBodyLength;

    public CampaignUpdatePublicationMediator(UserRepository userRepository,
                                             CampaignRepository campaignRepository,
                                             CampaignUpdateRepository updateRepository,
                                             CampaignUpdateMediaRepository mediaRepository,
                                             ContributionRepository contributionRepository,
                                             NotificationPublisher notificationPublisher,
                                             Validator validator,
                                             @Value("${app.campaign-updates.max-body-length:10000}") int maxBodyLength) {
        this.userRepository = userRepository;
        this.campaignRepository = campaignRepository;
        this.updateRepository = updateRepository;
        this.mediaRepository = mediaRepository;
        this.contributionRepository = contributionRepository;
        this.notificationPublisher = notificationPublisher;
        this.validator = validator;
        if (maxBodyLength < 1 || maxBodyLength > 50000) {
            throw new IllegalArgumentException("app.campaign-updates.max-body-length must be between 1 and 50000");
        }
        this.maxBodyLength = maxBodyLength;
    }

    @Transactional
    public CampaignUpdatePublicationResponse publish(User authenticatedUser, UUID campaignId,
                                                      CampaignUpdatePublicationRequest request) {
        User author = requireActiveCreator(authenticatedUser);
        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND"));
        if (!campaign.getCreatorId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CAMPAIGN_OWNER_REQUIRED");
        }
        if ((campaign.getStatus() != CampaignStatus.ACTIVE && campaign.getStatus() != CampaignStatus.PUBLISHED)
                || campaign.getStatus() == CampaignStatus.ACTIVE && !campaign.getDeadline().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAIGN_CLOSED");
        }
        validate(request);

        CampaignUpdate update = updateRepository.saveAndFlush(
                new CampaignUpdate(campaign, author.getId(), request.getTitle(), request.getBody()));
        List<CampaignUpdateMediaRequest> orderedMedia = orderMedia(request.getMedia());
        List<CampaignUpdateMedia> media = new ArrayList<>();
        for (int index = 0; index < orderedMedia.size(); index++) {
            CampaignUpdateMediaRequest item = orderedMedia.get(index);
            media.add(new CampaignUpdateMedia(
                    update,
                    CampaignUpdateMediaType.valueOf(item.getMediaType()),
                    item.getUrl(),
                    index));
        }
        List<CampaignUpdateMedia> savedMedia = mediaRepository.saveAll(media);
        mediaRepository.flush();

        List<UUID> sponsorIds = contributionRepository.findDistinctSponsorIdsByCampaignIdAndStatusExcludingCreator(
                campaignId, ContributionStatus.CONFIRMED, author.getId());
        int notificationsCreated = notificationPublisher.publishCampaignUpdate(campaign, update, sponsorIds);
        return toPublicationResponse(update, savedMedia, notificationsCreated);
    }

    @Transactional(readOnly = true)
    public CampaignUpdateWallResponse getWall(UUID campaignId, int page, int pageSize) {
        validatePage(page, pageSize);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND"));
        if (!PUBLIC_WALL_STATUSES.contains(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND");
        }

        Page<CampaignUpdate> updates = updateRepository.findByCampaignId(
                campaignId,
                PageRequest.of(page, pageSize, Sort.by(
                        Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        List<UUID> updateIds = updates.getContent().stream().map(CampaignUpdate::getId).toList();
        var media = updateIds.isEmpty()
                ? List.<CampaignUpdateMedia>of()
                : mediaRepository.findByUpdate_IdInOrderBySortOrderAscIdAsc(updateIds);
        var mediaByUpdate = media.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getUpdate().getId(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<CampaignUpdateWallResponse.Item> content = updates.getContent().stream()
                .map(update -> new CampaignUpdateWallResponse.Item(
                        update.getId(),
                        campaignId,
                        update.getAuthorId(),
                        update.getTitle(),
                        update.getBody(),
                        update.getPublishedAt(),
                        mediaResponses(mediaByUpdate.getOrDefault(update.getId(), List.of()))))
                .toList();
        return new CampaignUpdateWallResponse(content, page, pageSize, updates.getTotalElements(), updates.getTotalPages());
    }

    private User requireActiveCreator(User authenticatedUser) {
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getRole() != UserRole.CREATOR || user.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACTIVE_CREATOR_REQUIRED");
        }
        return user;
    }

    private void validate(CampaignUpdatePublicationRequest request) {
        if (request == null) {
            throw new CampaignUpdateValidationException(List.of(
                    new ValidationErrorDetail("body", "REQUIRED", "El contenido de la actualización es obligatorio.")));
        }
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<CampaignUpdatePublicationRequest> violation : validator.validate(request)) {
            String field = violation.getPropertyPath().toString();
            String constraint = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            String code = switch (constraint) {
                case "NotBlank", "NotNull" -> "REQUIRED";
                case "Size" -> "LENGTH";
                case "ValidExternalMediaUrl" -> "INVALID_URL";
                case "Pattern" -> field.endsWith("mediaType") ? "INVALID_MEDIA_TYPE" : "INVALID_FORMAT";
                case "Min" -> "INVALID_FORMAT";
                default -> "INVALID";
            };
            details.add(new ValidationErrorDetail(field, code, violation.getMessage()));
        }
        String title = request.getTitle();
        String body = request.getBody();
        if (title != null && containsUnsafeText(title, false)) {
            details.add(new ValidationErrorDetail("title", "INVALID_CONTENT", "El título debe ser texto plano."));
        }
        if (body != null) {
            if (body.length() > maxBodyLength) {
                details.add(new ValidationErrorDetail(
                        "body", "LENGTH", "El contenido no puede superar " + maxBodyLength + " caracteres."));
            }
            if (containsUnsafeText(body, true)) {
                details.add(new ValidationErrorDetail("body", "INVALID_CONTENT", "El contenido debe ser texto plano."));
            }
        }
        if (!details.isEmpty()) {
            details.sort(Comparator.comparing(ValidationErrorDetail::field)
                    .thenComparing(ValidationErrorDetail::code));
            throw new CampaignUpdateValidationException(details);
        }
    }

    private boolean containsUnsafeText(String value, boolean allowLineBreaks) {
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0 || FORBIDDEN_SCHEME.matcher(value).find()) return true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)
                    && !(allowLineBreaks && (character == '\n' || character == '\r' || character == '\t'))) {
                return true;
            }
        }
        return false;
    }

    private List<CampaignUpdateMediaRequest> orderMedia(List<CampaignUpdateMediaRequest> media) {
        return java.util.stream.IntStream.range(0, media.size())
                .boxed()
                .sorted(Comparator.comparingInt((Integer index) -> media.get(index).getSortOrder() == null
                                ? index : media.get(index).getSortOrder())
                        .thenComparingInt(Integer::intValue))
                .map(media::get)
                .toList();
    }

    private List<CampaignUpdateMediaResponse> mediaResponses(List<CampaignUpdateMedia> media) {
        return media.stream()
                .sorted(Comparator.comparingInt(CampaignUpdateMedia::getSortOrder)
                        .thenComparing(item -> item.getId().toString()))
                .map(item -> new CampaignUpdateMediaResponse(
                        item.getId(), item.getMediaType().name(), item.getUrl(), item.getSortOrder()))
                .toList();
    }

    private CampaignUpdatePublicationResponse toPublicationResponse(
            CampaignUpdate update, List<CampaignUpdateMedia> media, int notificationsCreated) {
        return new CampaignUpdatePublicationResponse(
                update.getId(),
                update.getCampaign().getId(),
                update.getAuthorId(),
                update.getTitle(),
                update.getBody(),
                update.getPublishedAt(),
                mediaResponses(media),
                notificationsCreated);
    }

    private void validatePage(int page, int pageSize) {
        List<ValidationErrorDetail> details = new ArrayList<>();
        if (page < 0) details.add(new ValidationErrorDetail("page", "MINIMUM", "La página debe ser mayor o igual a cero."));
        if (pageSize < 1 || pageSize > 50) {
            details.add(new ValidationErrorDetail("pageSize", "RANGE", "El tamaño de página debe estar entre 1 y 50."));
        }
        if (!details.isEmpty()) throw new CampaignUpdateValidationException(details);
    }
}
