package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.PublicUser;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignDraft;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignReward;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.DraftReward;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignDraftRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignUpdateRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignService {
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "Medio ambiente",
            "Educación",
            "Salud",
            "Ciencia",
            "Tecnología",
            "Arte",
            "Comunidad",
            "Emprendimiento"
    );

    private final CampaignDraftRepository campaignDraftRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignUpdateRepository campaignUpdateRepository;
    private final ContributionRepository contributionRepository;
    private final UserRepository userRepository;

    public CampaignService(CampaignDraftRepository campaignDraftRepository,
                          CampaignRepository campaignRepository,
                          CampaignUpdateRepository campaignUpdateRepository,
                          ContributionRepository contributionRepository,
                          UserRepository userRepository) {
        this.campaignDraftRepository = campaignDraftRepository;
        this.campaignRepository = campaignRepository;
        this.campaignUpdateRepository = campaignUpdateRepository;
        this.contributionRepository = contributionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DraftResponse createDraft(User creator, DraftCreateRequest request) {
        requireVerifiedCreator(creator);
        List<FieldError> errors = validateDraftFields(request.goalAmount(), request.durationDays(), request.category(), request.rewards());
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        CampaignDraft draft = new CampaignDraft(
                creator.getId(),
                normalizeText(request.title()),
                normalizeText(request.description()),
                request.goalAmount(),
                request.durationDays(),
                normalizeCategory(request.category()),
                request.mediaUrl());
        draft.setCompletionPercentage(calculateCompletionPercentage(draft));
        if (request.rewards() != null && !request.rewards().isEmpty()) {
            List<DraftReward> rewards = new ArrayList<>();
            for (int index = 0; index < request.rewards().size(); index++) {
                var rewardRequest = request.rewards().get(index);
                rewards.add(new DraftReward(draft, rewardRequest.title(), rewardRequest.description(), rewardRequest.minimumAmount(), index));
            }
            draft.setRewards(rewards);
        }
        draft = campaignDraftRepository.save(draft);
        return DraftResponse.from(draft);
    }

    @Transactional(readOnly = true)
    public PageResponse<DraftResponse> listDrafts(User creator, int page, int pageSize) {
        requireCreator(creator);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.from(campaignDraftRepository.findByCreatorId(creator.getId(), pageable).map(DraftResponse::from), page, pageSize);
    }

    @Transactional(readOnly = true)
    public DraftResponse getDraft(User creator, UUID draftId) {
        requireCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para acceder a este borrador");
        }
        return DraftResponse.from(draft);
    }

    @Transactional
    public DraftResponse updateDraft(User creator, UUID draftId, DraftUpdateRequest request) {
        requireCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para modificar este borrador");
        }

        BigDecimal goalAmount = request.goalAmount() != null ? request.goalAmount() : draft.getGoalAmount();
        Integer durationDays = request.durationDays() != null ? request.durationDays() : draft.getDurationDays();
        String category = request.category() != null ? normalizeCategory(request.category()) : draft.getCategory();
        String title = request.title() != null ? normalizeText(request.title()) : draft.getTitle();
        String description = request.description() != null ? normalizeText(request.description()) : draft.getDescription();
        String mediaUrl = request.mediaUrl() != null ? request.mediaUrl() : draft.getMediaUrl();

        List<FieldError> errors = validateDraftFields(goalAmount, durationDays, category, request.rewards() != null ? request.rewards() : convertRewards(draft.getRewards()));
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        draft.setTitle(title);
        draft.setDescription(description);
        draft.setGoalAmount(goalAmount);
        draft.setDurationDays(durationDays);
        draft.setCategory(category);
        draft.setMediaUrl(mediaUrl);
        draft.setCompletionPercentage(calculateCompletionPercentage(draft));
        draft.setSavedAt(Instant.now());

        if (request.rewards() != null) {
            List<DraftReward> rewards = new ArrayList<>();
            for (int index = 0; index < request.rewards().size(); index++) {
                var rewardRequest = request.rewards().get(index);
                DraftReward reward = new DraftReward(draft, rewardRequest.title(), rewardRequest.description(), rewardRequest.minimumAmount(), index);
                rewards.add(reward);
            }
            draft.setRewards(rewards);
        }

        return DraftResponse.from(campaignDraftRepository.save(draft));
    }

    @Transactional
    public void deleteDraft(User creator, UUID draftId) {
        requireCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para eliminar este borrador");
        }
        campaignDraftRepository.delete(draft);
    }

    @Transactional
    public CampaignDetailResponse publishDraft(User creator, UUID draftId) {
        requireVerifiedCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para publicar este borrador");
        }

        List<FieldError> errors = validateDraftFields(draft.getGoalAmount(), draft.getDurationDays(), draft.getCategory(), convertRewards(draft.getRewards()));
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        Instant now = Instant.now();
        Instant deadline = now.plusSeconds((long) draft.getDurationDays() * 24L * 60L * 60L);
        Campaign campaign = new Campaign(
                creator.getId(),
                draft.getTitle(),
                draft.getDescription(),
                draft.getGoalAmount(),
                deadline,
                normalizeCategory(draft.getCategory()),
                draft.getMediaUrl(),
                CampaignStatus.ACTIVE);
        campaign.setPublishedAt(now);
        campaign = campaignRepository.save(campaign);

        List<CampaignReward> rewards = new ArrayList<>();
        for (int index = 0; index < draft.getRewards().size(); index++) {
            var reward = draft.getRewards().get(index);
            rewards.add(new CampaignReward(campaign, reward.getTitle(), reward.getDescription(), reward.getMinimumAmount(), null, 0));
        }
        campaign.setRewards(rewards);
        campaignRepository.save(campaign);
        campaignDraftRepository.delete(draft);

        closeExpiredCampaigns();
        return campaignDetail(campaign);
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignSummaryResponse> listCampaigns(Integer page, Integer pageSize, String category, String status) {
        int safePage = Math.max(0, Objects.requireNonNullElse(page, 0));
        int safeSize = Math.max(1, Math.min(50, Objects.requireNonNullElse(pageSize, 10)));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "publishedAt"));
        closeExpiredCampaigns();

        Page<Campaign> campaigns;
        CampaignStatus campaignStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                campaignStatus = CampaignStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                campaigns = campaignRepository.findAll(pageable);
                return PageResponse.from(campaigns.map(this::campaignSummary), safePage, safeSize);
            }
        }

        if (category != null && !category.isBlank()) {
            String normalized = normalizeCategory(category);
            if (campaignStatus != null) {
                campaigns = campaignRepository.findByStatusAndCategoryIgnoreCase(campaignStatus, normalized, pageable);
            } else {
                campaigns = campaignRepository.findByCategoryIgnoreCase(normalized, pageable);
            }
        } else if (campaignStatus != null) {
            campaigns = campaignRepository.findByStatus(campaignStatus, pageable);
        } else {
            campaigns = campaignRepository.findAll(pageable);
        }
        return PageResponse.from(campaigns.map(this::campaignSummary), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaign(UUID campaignId) {
        closeExpiredCampaigns();
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        return campaignDetail(campaign);
    }

    @Transactional
    public CampaignUpdateResponse createCampaignUpdate(User creator, UUID campaignId, CampaignUpdateRequest request) {
        requireCreator(creator);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (!campaign.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para actualizar esta campaña");
        }
        if (request.title() == null || request.title().isBlank() || request.body() == null || request.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título y la descripción son obligatorios");
        }

        CampaignUpdate update = new CampaignUpdate(campaign, creator.getId(), request.title().trim(), request.body().trim());
        campaign.addUpdate(update);
        campaignRepository.save(campaign);
        return CampaignUpdateResponse.from(update);
    }

    @Transactional
    public void closeExpiredCampaigns() {
        List<Campaign> campaigns = campaignRepository.findAll();
        Instant now = Instant.now();
        for (Campaign campaign : campaigns) {
            if (campaign.getStatus() == CampaignStatus.ACTIVE && !campaign.getDeadline().isAfter(now)) {
                CampaignStatus nextStatus = progress(campaign).raisedAmount().compareTo(campaign.getGoalAmount()) >= 0
                        ? CampaignStatus.SUCCESSFUL
                        : CampaignStatus.FAILED;
                campaign.setStatus(nextStatus);
                campaignRepository.save(campaign);
            }
        }
    }

    private void requireCreator(User user) {
        if (user == null || user.getRole() != UserRole.CREATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los creadores pueden acceder a esta operación");
        }
    }

    private void requireVerifiedCreator(User user) {
        requireCreator(user);
        if (user.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El creador debe estar verificado");
        }
    }

    private CampaignDetailResponse campaignDetail(Campaign campaign) {
        User creator = userRepository.findById(campaign.getCreatorId()).orElse(null);
        return new CampaignDetailResponse(
                campaign.getId(),
                creator == null ? null : PublicUser.from(creator),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getGoalAmount(),
                campaign.getDeadline(),
                campaign.getCategory(),
                campaign.getMediaUrl(),
                campaign.getStatus(),
                campaign.getPublishedAt(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt(),
                rewardResponses(campaign),
                updateResponses(campaign),
                progress(campaign)
        );
    }

    private CampaignSummaryResponse campaignSummary(Campaign campaign) {
        return new CampaignSummaryResponse(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getGoalAmount(),
                campaign.getDeadline(),
                campaign.getCategory(),
                campaign.getMediaUrl(),
                campaign.getStatus(),
                campaign.getPublishedAt(),
                progress(campaign)
        );
    }

    private List<RewardResponse> rewardResponses(Campaign campaign) {
        return campaign.getRewards().stream()
                .sorted((left, right) -> Integer.compare(left.getTitle().compareTo(right.getTitle()), 0))
                .map(reward -> new RewardResponse(reward.getId(), reward.getTitle(), reward.getDescription(), reward.getMinimumAmount(), reward.getQuantity(), reward.getClaimedQuantity()))
                .toList();
    }

    private List<CampaignUpdateResponse> updateResponses(Campaign campaign) {
        return campaignUpdateRepository.findByCampaignIdOrderByPublishedAtDesc(campaign.getId()).stream()
                .map(CampaignUpdateResponse::from)
                .toList();
    }

    private CampaignProgress progress(Campaign campaign) {
        BigDecimal raised = contributionRepository.sumAmountByCampaignIdAndStatus(campaign.getId(), ContributionStatus.CONFIRMED);
        if (raised == null) raised = BigDecimal.ZERO;

        BigDecimal goal = campaign.getGoalAmount();
        BigDecimal remaining = goal.subtract(raised);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        int percentage = 0;
        if (goal.compareTo(BigDecimal.ZERO) > 0) {
            percentage = raised.multiply(BigDecimal.valueOf(100)).divide(goal, 0, RoundingMode.HALF_UP).intValue();
            if (percentage > 100) percentage = 100;
        }

        long sponsorsCount = contributionRepository.countDistinctSponsorByCampaignIdAndStatus(campaign.getId(), ContributionStatus.CONFIRMED);
        if (sponsorsCount < 0) sponsorsCount = 0;

        long secondsRemaining = Math.max(0L, campaign.getDeadline().getEpochSecond() - Instant.now().getEpochSecond());
        return new CampaignProgress(raised.setScale(2, RoundingMode.HALF_UP), remaining.setScale(2, RoundingMode.HALF_UP), percentage, sponsorsCount, secondsRemaining);
    }

    private int calculateCompletionPercentage(CampaignDraft draft) {
        if (draft == null) {
            return 0;
        }

        int completed = 0;
        int total = 0;

        if (draft.getTitle() != null && !draft.getTitle().isBlank()) {
            completed++;
        }
        total++;

        if (draft.getDescription() != null && !draft.getDescription().isBlank()) {
            completed++;
        }
        total++;

        if (draft.getGoalAmount() != null && draft.getGoalAmount().compareTo(BigDecimal.ZERO) > 0) {
            completed++;
        }
        total++;

        if (draft.getDurationDays() != null && draft.getDurationDays() >= 1 && draft.getDurationDays() <= 90) {
            completed++;
        }
        total++;

        if (draft.getCategory() != null && !draft.getCategory().isBlank()) {
            completed++;
        }
        total++;

        if (draft.getMediaUrl() != null && !draft.getMediaUrl().isBlank()) {
            completed++;
        }
        total++;

        if (draft.getRewards() != null && !draft.getRewards().isEmpty()) {
            int validRewardCount = 0;
            for (DraftReward reward : draft.getRewards()) {
                if (reward != null && reward.getTitle() != null && !reward.getTitle().isBlank() && reward.getMinimumAmount() != null && reward.getMinimumAmount().compareTo(BigDecimal.ZERO) > 0) {
                    validRewardCount++;
                }
            }
            if (!draft.getRewards().isEmpty() && validRewardCount == draft.getRewards().size()) {
                completed++;
            }
        }
        total++;

        if (total == 0) {
            return 0;
        }

        return Math.min(100, Math.max(0, (completed * 100) / total));
    }

    private List<FieldError> validateDraftFields(BigDecimal goalAmount, Integer durationDays, String category, List<RewardRequest> rewards) {
        List<FieldError> errors = new ArrayList<>();
        if (goalAmount == null) {
            errors.add(new FieldError("goalAmount", "REQUIRED"));
        } else if (goalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new FieldError("goalAmount", "INVALID"));
        }

        if (durationDays == null) {
            errors.add(new FieldError("durationDays", "REQUIRED"));
        } else if (durationDays < 1 || durationDays > 90) {
            errors.add(new FieldError("durationDays", "INVALID"));
        }

        if (category == null || category.isBlank()) {
            errors.add(new FieldError("category", "REQUIRED"));
        } else if (!VALID_CATEGORIES.contains(category.trim())) {
            errors.add(new FieldError("category", "INVALID"));
        }

        if (rewards != null) {
            for (int index = 0; index < rewards.size(); index++) {
                RewardRequest reward = rewards.get(index);
                if (reward.title() == null || reward.title().isBlank()) {
                    errors.add(new FieldError("rewards[" + index + "].title", "REQUIRED"));
                }
                if (reward.minimumAmount() == null || reward.minimumAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new FieldError("rewards[" + index + "].minimumAmount", "INVALID"));
                }
            }
        }
        return errors;
    }

    private List<RewardRequest> convertRewards(List<DraftReward> rewards) {
        if (rewards == null) return List.of();
        return rewards.stream().map(reward -> new RewardRequest(reward.getTitle(), reward.getDescription(), reward.getMinimumAmount())).toList();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeCategory(String category) {
        if (category == null) return null;
        String normalized = category.trim();
        return VALID_CATEGORIES.contains(normalized) ? normalized : normalized;
    }

    public record RewardRequest(String title, String description, BigDecimal minimumAmount) { }

    public record DraftCreateRequest(String title,
                                     String description,
                                     BigDecimal goalAmount,
                                     Integer durationDays,
                                     String category,
                                     String mediaUrl,
                                     List<RewardRequest> rewards) { }

    public record DraftUpdateRequest(String title,
                                     String description,
                                     BigDecimal goalAmount,
                                     Integer durationDays,
                                     String category,
                                     String mediaUrl,
                                     List<RewardRequest> rewards) { }

    public record PageResponse<T>(List<T> content,
                                  int page,
                                  int pageSize,
                                  long totalElements,
                                  int totalPages) {
        static <T> PageResponse<T> from(Page<T> result, int page, int pageSize) {
            return new PageResponse<>(result.getContent(), page, pageSize, result.getTotalElements(), result.getTotalPages());
        }
    }

    public record CampaignUpdateRequest(String title, String body) { }

    public record DraftResponse(UUID id,
                               String title,
                               String description,
                               BigDecimal goalAmount,
                               Integer durationDays,
                               String category,
                               String mediaUrl,
                               List<RewardResponse> rewards,
                               Integer completionPercentage,
                               Instant savedAt,
                               Instant createdAt,
                               Instant updatedAt) {
        static DraftResponse from(CampaignDraft draft) {
            return new DraftResponse(
                    draft.getId(),
                    draft.getTitle(),
                    draft.getDescription(),
                    draft.getGoalAmount(),
                    draft.getDurationDays(),
                    draft.getCategory(),
                    draft.getMediaUrl(),
                    draft.getRewards().stream()
                            .sorted((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()))
                            .map(reward -> new RewardResponse(reward.getId(), reward.getTitle(), reward.getDescription(), reward.getMinimumAmount(), null, null))
                            .toList(),
                    draft.getCompletionPercentage(),
                    draft.getSavedAt(),
                    draft.getCreatedAt(),
                    draft.getUpdatedAt());
        }
    }

    public record RewardResponse(UUID id, String title, String description, BigDecimal minimumAmount, Integer quantity, Integer claimedQuantity) { }

    public record CampaignProgress(BigDecimal raisedAmount,
                                  BigDecimal remainingAmount,
                                  Integer percentage,
                                  Long sponsorsCount,
                                  Long secondsRemaining) { }

    public record CampaignSummaryResponse(UUID id,
                                         String title,
                                         String description,
                                         BigDecimal goalAmount,
                                         Instant deadline,
                                         String category,
                                         String mediaUrl,
                                         CampaignStatus status,
                                         Instant publishedAt,
                                         CampaignProgress progress) { }

    public record CampaignDetailResponse(UUID id,
                                        PublicUser creator,
                                        String title,
                                        String description,
                                        BigDecimal goalAmount,
                                        Instant deadline,
                                        String category,
                                        String mediaUrl,
                                        CampaignStatus status,
                                        Instant publishedAt,
                                        Instant createdAt,
                                        Instant updatedAt,
                                        List<RewardResponse> rewards,
                                        List<CampaignUpdateResponse> updates,
                                        CampaignProgress progress) { }

    public record CampaignUpdateResponse(UUID id, UUID campaignId, UUID authorId, String title, String body, Instant publishedAt) {
        static CampaignUpdateResponse from(CampaignUpdate update) {
            return new CampaignUpdateResponse(update.getId(), update.getCampaign().getId(), update.getAuthorId(), update.getTitle(), update.getBody(), update.getPublishedAt());
        }
    }

    public record FieldError(String field, String code) { }

    public static class FieldValidationException extends RuntimeException {
        private final List<FieldError> errors;

        public FieldValidationException(List<FieldError> errors) {
            super(errors.isEmpty() ? "Validation error" : errors.getFirst().field() + " is invalid");
            this.errors = List.copyOf(errors);
        }

        public List<FieldError> getErrors() { return errors; }
    }
}
