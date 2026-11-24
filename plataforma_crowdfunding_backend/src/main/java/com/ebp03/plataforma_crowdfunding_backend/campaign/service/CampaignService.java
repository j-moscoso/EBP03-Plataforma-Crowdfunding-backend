package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.PublicUser;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignDraft;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignMediaType;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.FundingModel;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignReward;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.DraftReward;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.StrictIntegerDeserializer;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignDraftRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignSponsorRankingProjection;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignUpdateRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.SubscriptionCampaignAllocationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;
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
    private final SubscriptionCampaignAllocationRepository subscriptionAllocations;
    private final UserRepository userRepository;
    private final CampaignClosureService campaignClosureService;

    public CampaignService(CampaignDraftRepository campaignDraftRepository,
                          CampaignRepository campaignRepository,
                          CampaignUpdateRepository campaignUpdateRepository,
                          ContributionRepository contributionRepository,
                          SubscriptionCampaignAllocationRepository subscriptionAllocations,
                          UserRepository userRepository,
                          CampaignClosureService campaignClosureService) {
        this.campaignDraftRepository = campaignDraftRepository;
        this.campaignRepository = campaignRepository;
        this.campaignUpdateRepository = campaignUpdateRepository;
        this.contributionRepository = contributionRepository;
        this.subscriptionAllocations = subscriptionAllocations;
        this.userRepository = userRepository;
        this.campaignClosureService = campaignClosureService;
    }

    @Transactional
    public DraftResponse createDraft(User creator, DraftCreateRequest request) {
        requireActiveCreator(creator);
        List<FieldError> errors = validateDraftFields(request.goalAmount(), request.durationDays(), request.category(), request.rewards());
        FundingModel fundingModel = parseFundingModel(request.fundingModel(), true);
        if (request.fundingModel() != null && fundingModel == null) {
            errors.add(new FieldError("fundingModel", "INVALID"));
        }
        String currency = normalizeCurrency(request.currency());
        if (request.currency() != null && currency == null) {
            errors.add(new FieldError("currency", "INVALID"));
        }
        CampaignMediaType mediaType = parseMediaType(request.mediaType());
        if (request.mediaType() != null && mediaType == null) {
            errors.add(new FieldError("mediaType", "INVALID"));
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        CampaignDraft draft = new CampaignDraft(
                creator.getId(),
                normalizeText(request.title()),
                normalizeText(request.description()),
                request.goalAmount(),
                request.durationDays(),
                normalizeCategory(request.category()),
                request.mediaUrl(),
                currency,
                mediaType,
                fundingModel);
        draft.setCompletionPercentage(calculateCompletionPercentage(draft));
        if (request.rewards() != null && !request.rewards().isEmpty()) {
            List<DraftReward> rewards = new ArrayList<>();
            for (int index = 0; index < request.rewards().size(); index++) {
                var rewardRequest = request.rewards().get(index);
                rewards.add(new DraftReward(draft, normalizeText(rewardRequest.title()),
                        normalizeText(rewardRequest.description()), rewardRequest.minimumAmount(),
                        rewardRequest.quantity(), rewardRequest.estimatedDeliveryDate(), index));
            }
            draft.setRewards(rewards);
        }
        draft = campaignDraftRepository.save(draft);
        return DraftResponse.from(draft);
    }

    @Transactional(readOnly = true)
    public PageResponse<DraftResponse> listDrafts(User creator, int page, int pageSize) {
        requireActiveCreator(creator);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.from(campaignDraftRepository.findByCreatorId(creator.getId(), pageable).map(DraftResponse::from), page, pageSize);
    }

    @Transactional(readOnly = true)
    public DraftResponse getDraft(User creator, UUID draftId) {
        requireActiveCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para acceder a este borrador");
        }
        return DraftResponse.from(draft);
    }

    @Transactional
    public DraftResponse updateDraft(User creator, UUID draftId, DraftUpdateRequest request) {
        requireActiveCreator(creator);
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
        String currency = request.currency() != null ? normalizeCurrency(request.currency()) : draft.getCurrency();
        CampaignMediaType mediaType = request.mediaType() != null
                ? parseMediaType(request.mediaType())
                : draft.getMediaType();
        FundingModel fundingModel = request.fundingModel() != null
                ? parseFundingModel(request.fundingModel(), false)
                : draft.getFundingModel();

        List<FieldError> errors = validateDraftFields(goalAmount, durationDays, category, request.rewards() != null ? request.rewards() : convertRewards(draft.getRewards()));
        if (request.currency() != null && currency == null) {
            errors.add(new FieldError("currency", "INVALID"));
        }
        if (request.mediaType() != null && mediaType == null) {
            errors.add(new FieldError("mediaType", "INVALID"));
        }
        if (request.fundingModel() != null && fundingModel == null) {
            errors.add(new FieldError("fundingModel", "INVALID"));
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        draft.setTitle(title);
        draft.setDescription(description);
        draft.setGoalAmount(goalAmount);
        draft.setDurationDays(durationDays);
        draft.setCategory(category);
        draft.setMediaUrl(mediaUrl);
        draft.setMediaType(mediaType);
        draft.setCurrency(currency);
        draft.setFundingModel(fundingModel);
        draft.setCompletionPercentage(calculateCompletionPercentage(draft));
        draft.setSavedAt(Instant.now());

        if (request.rewards() != null) {
            List<DraftReward> rewards = new ArrayList<>();
            for (int index = 0; index < request.rewards().size(); index++) {
                var rewardRequest = request.rewards().get(index);
                DraftReward reward = new DraftReward(draft, normalizeText(rewardRequest.title()),
                        normalizeText(rewardRequest.description()), rewardRequest.minimumAmount(),
                        rewardRequest.quantity(), rewardRequest.estimatedDeliveryDate(), index);
                rewards.add(reward);
            }
            draft.setRewards(rewards);
        }

        return DraftResponse.from(campaignDraftRepository.save(draft));
    }

    @Transactional
    public void deleteDraft(User creator, UUID draftId) {
        requireActiveCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para eliminar este borrador");
        }
        campaignDraftRepository.delete(draft);
    }

    @Transactional
    public CampaignDetailResponse publishDraft(User creator, UUID draftId) {
        requireActiveCreator(creator);
        CampaignDraft draft = campaignDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft no encontrado"));
        if (!draft.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para publicar este borrador");
        }

        List<FieldError> errors = validateDraftFields(draft.getGoalAmount(), draft.getDurationDays(), draft.getCategory(), convertRewards(draft.getRewards()));
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
        if (draft.getCurrency() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CAMPAIGN_CURRENCY_REQUIRED");
        }

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
                draft.getCurrency(),
                draft.getMediaType(),
                draft.getFundingModel(),
                CampaignStatus.ACTIVE);
        campaign.setPublishedAt(now);
        campaign = campaignRepository.save(campaign);

        List<CampaignReward> rewards = new ArrayList<>();
        for (int index = 0; index < draft.getRewards().size(); index++) {
            var reward = draft.getRewards().get(index);
            rewards.add(new CampaignReward(campaign, reward.getTitle(), reward.getDescription(),
                    reward.getMinimumAmount(), reward.getQuantity(), 0, reward.getEstimatedDeliveryDate()));
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
    public PageResponse<CampaignSummaryResponse> listMyCampaigns(User creator, Integer page, Integer pageSize, String status) {
        requireActiveCreator(creator);
        int safePage = Math.max(0, Objects.requireNonNullElse(page, 0));
        int safeSize = Math.max(1, Math.min(50, Objects.requireNonNullElse(pageSize, 10)));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<Campaign> campaigns;
        if (status == null || status.isBlank()) {
            campaigns = campaignRepository.findByCreatorId(creator.getId(), pageable);
        } else {
            try {
                campaigns = campaignRepository.findByCreatorIdAndStatus(
                        creator.getId(), CampaignStatus.valueOf(status.trim().toUpperCase()), pageable);
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de campaña inválido");
            }
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

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(UUID campaignId) {
        closeExpiredCampaigns();
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        CampaignProgress progress = progress(campaign);
        return new ProgressResponse(campaign.getGoalAmount(), progress.raisedAmount(), progress.remainingAmount(),
                progress.percentage(), progress.sponsorsCount(), progress.secondsRemaining(), campaign.getStatus());
    }

    @Transactional
    public void closeExpiredCampaigns() {
        campaignClosureService.processExpiredCampaigns(Instant.now());
    }

    @Transactional
    public FundingModelResponse updateFundingModel(User creator, UUID campaignId, FundingModelUpdateRequest request) {
        requireActiveCreator(creator);
        if (request == null) {
            throw new FieldValidationException(List.of(new FieldError("fundingModel", "REQUIRED")));
        }
        FundingModel fundingModel = parseFundingModel(request.fundingModel(), false);
        if (fundingModel == null) {
            throw new FieldValidationException(List.of(new FieldError("fundingModel", "INVALID")));
        }

        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (!campaign.getCreatorId().equals(creator.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para modificar esta campaña");
        }
        if (campaign.getStatus() == CampaignStatus.SUCCESSFUL
                || campaign.getStatus() == CampaignStatus.FAILED
                || campaign.getStatus() == CampaignStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "FUNDING_MODEL_IMMUTABLE");
        }
        if (!campaign.getDeadline().isAfter(Instant.now())
                || contributionRepository.existsByCampaignIdAndStatus(campaignId, ContributionStatus.CONFIRMED)
                || contributionRepository.existsByCampaignIdAndStatus(campaignId, ContributionStatus.HELD)
                || contributionRepository.existsByCampaignIdAndStatus(campaignId, ContributionStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "FUNDING_MODEL_ALREADY_IN_USE");
        }

        campaign.setFundingModel(fundingModel);
        campaignRepository.save(campaign);
        return new FundingModelResponse(campaign.getId(), campaign.getFundingModel(), campaign.getUpdatedAt());
    }

    private void requireActiveCreator(User user) {
        if (user == null || user.getRole() != UserRole.CREATOR || user.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los creadores pueden acceder a esta operación");
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
                campaign.getMediaType(),
                canonicalCurrency(campaign.getCurrency()),
                campaignCurrencyStatus(campaign),
                campaign.getFundingModel(),
                campaign.getStatus(),
                campaign.getPublishedAt(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt(),
                rewardResponses(campaign),
                updateResponses(campaign),
                progress(campaign),
                topSponsors(campaign.getId())
        );
    }

    private List<CampaignSponsorRankingResponse> topSponsors(UUID campaignId) {
        return contributionRepository.findTopSponsorsByCampaignId(campaignId).stream()
                .map(sponsor -> new CampaignSponsorRankingResponse(
                        UUID.fromString(sponsor.getSponsorId()), sponsor.getSponsorName(), sponsor.getTotalAmount()))
                .toList();
    }

    private CampaignSummaryResponse campaignSummary(Campaign campaign) {
        User creator = userRepository.findById(campaign.getCreatorId()).orElse(null);
        return new CampaignSummaryResponse(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getGoalAmount(),
                campaign.getDeadline(),
                campaign.getCategory(),
                campaign.getMediaUrl(),
                campaign.getMediaType(),
                canonicalCurrency(campaign.getCurrency()),
                campaignCurrencyStatus(campaign),
                campaign.getFundingModel(),
                campaign.getStatus(),
                campaign.getPublishedAt(),
                progress(campaign),
                creator == null ? null : new CampaignCreatorSummary(creator.getId(), creator.getName())
        );
    }

    private List<RewardResponse> rewardResponses(Campaign campaign) {
        return campaign.getRewards().stream()
                .sorted((left, right) -> Integer.compare(left.getTitle().compareTo(right.getTitle()), 0))
                .map(reward -> rewardResponse(reward.getId(), reward.getTitle(), reward.getDescription(),
                        reward.getMinimumAmount(), reward.getEstimatedDeliveryDate(),
                        reward.getQuantity(), reward.getClaimedQuantity()))
                .toList();
    }

    static RewardResponse rewardResponse(UUID id, String title, String description, BigDecimal minimumAmount,
                                         LocalDate estimatedDeliveryDate, Integer quantity, Integer claimedQuantity) {
        int claimed = Objects.requireNonNullElse(claimedQuantity, 0);
        boolean available = quantity == null || claimed < quantity;
        Integer remainingQuantity = quantity == null ? null : Math.max(0, quantity - claimed);
        return new RewardResponse(id, title, description, minimumAmount, estimatedDeliveryDate, quantity,
                claimedQuantity, available, remainingQuantity);
    }

    private List<CampaignUpdateResponse> updateResponses(Campaign campaign) {
        return campaignUpdateRepository.findByCampaignIdOrderByPublishedAtDesc(campaign.getId()).stream()
                .map(CampaignUpdateResponse::from)
                .toList();
    }

    private CampaignProgress progress(Campaign campaign) {
        BigDecimal raised;
        if (campaign.getFundingModel() == FundingModel.FIXED) {
            raised = contributionRepository.sumAmountByCampaignIdAndStatuses(
                    campaign.getId(), List.of(ContributionStatus.CONFIRMED, ContributionStatus.HELD));
        } else {
            raised = contributionRepository.sumAmountByCampaignIdAndStatus(
                    campaign.getId(), ContributionStatus.CONFIRMED);
        }
        if (raised == null) raised = BigDecimal.ZERO;
        if (campaign.getCurrency() != null) {
            raised = raised.add(subscriptionAllocations.sumAmountByCampaignIdAndCurrency(
                    campaign.getId(), campaign.getCurrency()));
        }

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

        List<ContributionStatus> countedStatuses = campaign.getFundingModel() == FundingModel.FIXED
                ? List.of(ContributionStatus.CONFIRMED, ContributionStatus.HELD)
                : List.of(ContributionStatus.CONFIRMED);
        Set<UUID> sponsors = new LinkedHashSet<>(
                contributionRepository.findDistinctSponsorIdsByCampaignIdAndStatuses(
                        campaign.getId(), countedStatuses));
        sponsors.addAll(subscriptionAllocations.findDistinctSponsorIdsByCampaignId(campaign.getId()));
        long sponsorsCount = sponsors.size();
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
        } else if (!validMoney(goalAmount)) {
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
                if (reward == null) {
                    errors.add(new FieldError("rewards[" + index + "]", "INVALID"));
                    continue;
                }
                String prefix = "rewards[" + index + "].";
                if (reward.title() == null || reward.title().trim().isEmpty()) {
                    errors.add(new FieldError(prefix + "title", "REQUIRED"));
                } else if (reward.title().trim().length() > 120) {
                    errors.add(new FieldError(prefix + "title", "MAX_LENGTH"));
                }
                if (reward.description() == null || reward.description().trim().isEmpty()) {
                    errors.add(new FieldError(prefix + "description", "REQUIRED"));
                } else if (reward.description().trim().length() > 2000) {
                    errors.add(new FieldError(prefix + "description", "MAX_LENGTH"));
                }
                if (reward.minimumAmount() == null) {
                    errors.add(new FieldError(prefix + "minimumAmount", "REQUIRED"));
                } else if (!validMoney(reward.minimumAmount())) {
                    errors.add(new FieldError(prefix + "minimumAmount", "INVALID"));
                } else if (goalAmount != null && reward.minimumAmount().compareTo(goalAmount) > 0) {
                    errors.add(new FieldError(
                            prefix + "minimumAmount",
                            "MINIMUM_AMOUNT_EXCEEDS_GOAL",
                            "El monto mínimo de la recompensa no puede superar la meta de la campaña."));
                }
                if (reward.estimatedDeliveryDate() == null) {
                    errors.add(new FieldError(prefix + "estimatedDeliveryDate", "REQUIRED"));
                }
                if (reward.quantity() != null && reward.quantity() <= 0) {
                    errors.add(new FieldError(prefix + "quantity", "INVALID"));
                }
            }
        }
        return errors;
    }

    private boolean validMoney(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ZERO) > 0
                && amount.scale() <= 2;
    }

    private List<RewardRequest> convertRewards(List<DraftReward> rewards) {
        if (rewards == null) return List.of();
        return rewards.stream().map(reward -> new RewardRequest(reward.getTitle(), reward.getDescription(),
                reward.getMinimumAmount(), reward.getEstimatedDeliveryDate(), reward.getQuantity())).toList();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeCategory(String category) {
        if (category == null) return null;
        String normalized = category.trim();
        return VALID_CATEGORIES.contains(normalized) ? normalized : normalized;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String normalized = currency.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }

    private String campaignCurrencyStatus(Campaign campaign) {
        return canonicalCurrency(campaign.getCurrency()) == null ? "UNAVAILABLE" : "CONFIGURED";
    }

    private String canonicalCurrency(String currency) {
        return normalizeCurrency(currency);
    }

    private CampaignMediaType parseMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return CampaignMediaType.IMAGE;
        }
        try {
            return CampaignMediaType.valueOf(mediaType.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private FundingModel parseFundingModel(String value, boolean omittedDefaultsToFixed) {
        if (value == null || value.isBlank()) {
            return omittedDefaultsToFixed ? FundingModel.FIXED : null;
        }
        try {
            return FundingModel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record RewardRequest(String title, String description, BigDecimal minimumAmount,
                                LocalDate estimatedDeliveryDate,
                                @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer quantity) { }

    public record FundingModelUpdateRequest(String fundingModel) { }

    public record FundingModelResponse(UUID campaignId, FundingModel fundingModel, Instant updatedAt) { }

    public record DraftCreateRequest(String title,
                                     String description,
                                     BigDecimal goalAmount,
                                     Integer durationDays,
                                     String category,
                                     String mediaUrl,
                                     List<RewardRequest> rewards,
                                     String currency,
                                     String mediaType,
                                     String fundingModel) { }

    public record DraftUpdateRequest(String title,
                                     String description,
                                     BigDecimal goalAmount,
                                     Integer durationDays,
                                     String category,
                                     String mediaUrl,
                                     List<RewardRequest> rewards,
                                     String currency,
                                     String mediaType,
                                     String fundingModel) { }

    public record PageResponse<T>(List<T> content,
                                  int page,
                                  int pageSize,
                                  long totalElements,
                                  int totalPages) {
        static <T> PageResponse<T> from(Page<T> result, int page, int pageSize) {
            return new PageResponse<>(result.getContent(), page, pageSize, result.getTotalElements(), result.getTotalPages());
        }
    }

    public record DraftResponse(UUID id,
                               String title,
                               String description,
                               BigDecimal goalAmount,
                               Integer durationDays,
                               String category,
                               String mediaUrl,
                               CampaignMediaType mediaType,
                               String currency,
                               FundingModel fundingModel,
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
                    draft.getMediaType(),
                    draft.getCurrency(),
                    draft.getFundingModel(),
                    draft.getRewards().stream()
                            .sorted((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()))
                            .map(reward -> rewardResponse(reward.getId(), reward.getTitle(), reward.getDescription(),
                                    reward.getMinimumAmount(), reward.getEstimatedDeliveryDate(), reward.getQuantity(), 0))
                            .toList(),
                    draft.getCompletionPercentage(),
                    draft.getSavedAt(),
                    draft.getCreatedAt(),
                    draft.getUpdatedAt());
        }
    }

    public record RewardResponse(UUID id, String title, String description, BigDecimal minimumAmount,
                                 LocalDate estimatedDeliveryDate, Integer quantity, Integer claimedQuantity,
                                 boolean available, Integer remainingQuantity) { }

    public record CampaignProgress(BigDecimal raisedAmount,
                                  BigDecimal remainingAmount,
                                  Integer percentage,
                                  Long sponsorsCount,
                                  Long secondsRemaining) { }

    public record ProgressResponse(BigDecimal goalAmount, BigDecimal raisedAmount, BigDecimal remainingAmount,
                                   Integer percentage, Long sponsorsCount, Long secondsRemaining,
                                   CampaignStatus status) { }

    public record CampaignSummaryResponse(UUID id,
                                         String title,
                                         String description,
                                         BigDecimal goalAmount,
                                         Instant deadline,
                                         String category,
                                         String mediaUrl,
                                         CampaignMediaType mediaType,
                                         String currency,
                                         String currencyStatus,
                                         FundingModel fundingModel,
                                         CampaignStatus status,
                                         Instant publishedAt,
                                         CampaignProgress progress,
                                         CampaignCreatorSummary creator) { }

    public record CampaignCreatorSummary(UUID id, String name) { }

    public record CampaignDetailResponse(UUID id,
                                        PublicUser creator,
                                        String title,
                                        String description,
                                        BigDecimal goalAmount,
                                        Instant deadline,
                                        String category,
                                        String mediaUrl,
                                        CampaignMediaType mediaType,
                                        String currency,
                                        String currencyStatus,
                                        FundingModel fundingModel,
                                        CampaignStatus status,
                                        Instant publishedAt,
                                        Instant createdAt,
                                        Instant updatedAt,
                                        List<RewardResponse> rewards,
                                        List<CampaignUpdateResponse> updates,
                                        CampaignProgress progress,
                                        List<CampaignSponsorRankingResponse> topSponsors) { }

    public record CampaignSponsorRankingResponse(UUID sponsorId, String sponsorName, BigDecimal totalAmount) { }

    public record CampaignUpdateResponse(UUID id, UUID campaignId, UUID authorId, String title, String body, Instant publishedAt) {
        static CampaignUpdateResponse from(CampaignUpdate update) {
            return new CampaignUpdateResponse(update.getId(), update.getCampaign().getId(), update.getAuthorId(), update.getTitle(), update.getBody(), update.getPublishedAt());
        }
    }

    public record FieldError(String field, String code, String message) {
        public FieldError(String field, String code) {
            this(field, code, code);
        }
    }

    public static class FieldValidationException extends RuntimeException {
        private final List<FieldError> errors;

        public FieldValidationException(List<FieldError> errors) {
            super(errors.isEmpty() ? "Validation error" : errors.getFirst().field() + " is invalid");
            this.errors = List.copyOf(errors);
        }

        public List<FieldError> getErrors() { return errors; }
    }
}
