package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignDiscussionRequest;
import com.ebp03.plataforma_crowdfunding_backend.campaign.api.CampaignFaqRequest;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignDiscussionComment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignDiscussionCommentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampaignDiscussionService {
    private static final Pattern DANGEROUS_HTML = Pattern.compile(
            "(?is)<\\s*(script|iframe|object|embed|style)|javascript\\s*:|vbscript\\s*:|data\\s*:");
    private static final Set<CampaignStatus> PUBLIC_STATUSES = Set.of(
            CampaignStatus.PUBLISHED, CampaignStatus.ACTIVE, CampaignStatus.SUCCESSFUL,
            CampaignStatus.FAILED, CampaignStatus.CANCELLED);

    private final CampaignRepository campaignRepository;
    private final CampaignDiscussionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final int maxBodyLength;

    public CampaignDiscussionService(CampaignRepository campaignRepository,
                                     CampaignDiscussionCommentRepository commentRepository,
                                     UserRepository userRepository,
                                     @Value("${app.campaign-discussion.max-body-length:1000}") int maxBodyLength) {
        this.campaignRepository = campaignRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        if (maxBodyLength < 1 || maxBodyLength > 50000) {
            throw new IllegalArgumentException("app.campaign-discussion.max-body-length must be between 1 and 50000");
        }
        this.maxBodyLength = maxBodyLength;
    }

    @Transactional(readOnly = true)
    public DiscussionPageResponse getDiscussion(UUID campaignId, int page, int pageSize) {
        Campaign campaign = requireVisibleCampaign(campaignId);
        validatePagination(page, pageSize);
        Page<CampaignDiscussionComment> result = commentRepository.findByCampaignId(
                campaign.getId(),
                PageRequest.of(page, pageSize, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
        return toPage(result, page, pageSize);
    }

    @Transactional
    public CommentResponse publishQuestion(User authenticatedUser, UUID campaignId, CampaignDiscussionRequest request) {
        User author = requireActiveParticipant(authenticatedUser);
        Campaign campaign = requireWritableCampaign(campaignId);
        String body = validateBody(request == null ? null : request.getBody());
        CampaignDiscussionComment comment = commentRepository.saveAndFlush(
                new CampaignDiscussionComment(campaign, author.getId(), null, body));
        return toComment(comment, author.getName());
    }

    @Transactional
    public CommentResponse reply(User authenticatedUser, UUID campaignId, UUID commentId,
                                 CampaignDiscussionRequest request) {
        User owner = requireActiveOwner(authenticatedUser);
        Campaign campaign = requireWritableCampaign(campaignId);
        if (!campaign.getCreatorId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CAMPAIGN_OWNER_REQUIRED");
        }

        CampaignDiscussionComment question = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
        if (!question.getCampaign().getId().equals(campaignId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMMENT_CAMPAIGN_MISMATCH");
        }
        if (question.getParentCommentId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMMENT_IS_NOT_ROOT");
        }

        String body = validateBody(request == null ? null : request.getBody());
        CampaignDiscussionComment reply = commentRepository.saveAndFlush(
                new CampaignDiscussionComment(campaign, owner.getId(), question.getId(), body));
        return toComment(reply, owner.getName());
    }

    @Transactional
    public CommentResponse convertToFaq(User authenticatedUser, UUID campaignId, UUID commentId,
                                        CampaignFaqRequest request) {
        User owner = requireActiveOwner(authenticatedUser);
        Campaign campaign = requireVisibleCampaign(campaignId);
        if (!campaign.getCreatorId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CAMPAIGN_OWNER_REQUIRED");
        }

        CampaignDiscussionComment answer = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));
        if (!answer.getCampaign().getId().equals(campaignId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COMMENT_CAMPAIGN_MISMATCH");
        }
        if (answer.getParentCommentId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ROOT_COMMENT_CANNOT_BE_FAQ");
        }
        if (request != null && Boolean.FALSE.equals(request.getFaq())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FAQ_UNMARKING_NOT_SUPPORTED");
        }
        if (!answer.isFaq() && commentRepository.existsByParentCommentIdAndOfficialAnswerTrue(
                answer.getParentCommentId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OFFICIAL_ANSWER_ALREADY_EXISTS");
        }

        if (!answer.isOfficialAnswer() || !answer.isFaq()) {
            answer.markOfficialFaq();
            commentRepository.save(answer);
        }
        return toComment(answer, owner.getName());
    }

    @Transactional(readOnly = true)
    public FaqPageResponse getFaq(UUID campaignId, int page, int pageSize) {
        Campaign campaign = requireVisibleCampaign(campaignId);
        validatePagination(page, pageSize);
        Page<CampaignDiscussionComment> result = commentRepository.findByCampaignIdAndFaqTrueAndOfficialAnswerTrue(
                campaign.getId(),
                PageRequest.of(page, pageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        Map<UUID, String> names = namesFor(result.getContent());
        List<FaqItemResponse> items = new ArrayList<>();
        for (CampaignDiscussionComment answer : result.getContent()) {
            if (answer.getParentCommentId() == null) continue;
            CampaignDiscussionComment question = commentRepository.findById(answer.getParentCommentId()).orElse(null);
            if (question == null || !question.getCampaign().getId().equals(campaignId)
                    || question.getParentCommentId() != null) continue;
            items.add(new FaqItemResponse(
                    toComment(answer, names.getOrDefault(answer.getAuthorId(), "Usuario")),
                    toComment(question, names.getOrDefault(question.getAuthorId(), "Usuario"))));
        }
        return new FaqPageResponse(items, page, pageSize, items.size(), result.getTotalPages());
    }

    private Campaign requireVisibleCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CAMPAÑA_NO_ENCONTRADA"));
        if (!PUBLIC_STATUSES.contains(campaign.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CAMPAÑA_NO_VISIBLE");
        }
        return campaign;
    }

    private Campaign requireWritableCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CAMPAÑA_NO_ENCONTRADA"));
        if (!PUBLIC_STATUSES.contains(campaign.getStatus())
                || (campaign.getStatus() == CampaignStatus.ACTIVE
                && campaign.getDeadline().isBefore(Instant.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAÑA_CERRADA");
        }
        return campaign;
    }

    private User requireActiveParticipant(User user) {
        if (user == null || user.getStatus() != AccountStatus.ACTIVE
                || (user.getRole() != UserRole.SPONSOR && user.getRole() != UserRole.CREATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "USUARIO_NO_AUTORIZADO");
        }
        return user;
    }

    private User requireActiveOwner(User user) {
        if (user == null || user.getRole() != UserRole.CREATOR || user.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CREADOR_ACTIVO_REQUERIDO");
        }
        return user;
    }

    private String validateBody(String body) {
        if (body == null) {
            throw new DiscussionFieldValidationException(List.of(
                    new ValidationErrorDetail("body", "REQUIRED", "El comentario no puede estar vacío.")));
        }
        String trimmed = body.strip();
        if (trimmed.isEmpty()) {
            throw new DiscussionFieldValidationException(List.of(
                    new ValidationErrorDetail("body", "REQUIRED", "El comentario no puede estar vacío.")));
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxBodyLength) {
            throw new DiscussionFieldValidationException(List.of(
                    new ValidationErrorDetail("body", "MAX_LENGTH",
                            "El comentario no puede superar " + maxBodyLength + " caracteres.")));
        }
        if (DANGEROUS_HTML.matcher(trimmed).find()) {
            throw new DiscussionFieldValidationException(List.of(
                    new ValidationErrorDetail("body", "INVALID_HTML", "No se permite contenido HTML peligroso.")));
        }
        return trimmed;
    }

    private void validatePagination(int page, int pageSize) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        if (page < 0) errors.add(new ValidationErrorDetail("page", "INVALID", "La página no puede ser negativa."));
        if (pageSize < 1) errors.add(new ValidationErrorDetail("pageSize", "INVALID", "El tamaño debe ser mayor que cero."));
        if (pageSize > 50) errors.add(new ValidationErrorDetail("pageSize", "MAX_LENGTH", "El tamaño máximo es 50."));
        if (!errors.isEmpty()) throw new DiscussionFieldValidationException(errors);
    }

    private DiscussionPageResponse toPage(Page<CampaignDiscussionComment> result, int page, int pageSize) {
        Map<UUID, String> names = namesFor(result.getContent());
        List<CommentResponse> content = result.getContent().stream()
                .map(comment -> toComment(comment, names.getOrDefault(comment.getAuthorId(), "Usuario")))
                .toList();
        return new DiscussionPageResponse(content, page, pageSize,
                result.getTotalElements(), result.getTotalPages());
    }

    private Map<UUID, String> namesFor(List<CampaignDiscussionComment> comments) {
        Set<UUID> ids = comments.stream().map(CampaignDiscussionComment::getAuthorId).collect(Collectors.toSet());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (left, right) -> left));
    }

    private CommentResponse toComment(CampaignDiscussionComment comment, String authorName) {
        return new CommentResponse(comment.getId(), comment.getCampaign().getId(), comment.getAuthorId(),
                authorName, comment.getParentCommentId(), comment.getBody(),
                comment.isOfficialAnswer(), comment.isFaq(), comment.getCreatedAt());
    }

    public record CommentResponse(UUID id, UUID campaignId, UUID authorId, String authorName,
                                  UUID parentCommentId, String body, boolean isOfficialAnswer,
                                  boolean isFaq, Instant createdAt) { }

    public record DiscussionPageResponse(List<CommentResponse> content, int page, int pageSize,
                                         long totalElements, int totalPages) { }

    public record FaqItemResponse(UUID id, UUID campaignId, UUID authorId, String authorName,
                                  UUID parentCommentId, String body, boolean isOfficialAnswer,
                                  boolean isFaq, Instant createdAt, CommentResponse answer,
                                  CommentResponse question) {
        public FaqItemResponse(CommentResponse answer, CommentResponse question) {
            this(answer.id(), answer.campaignId(), answer.authorId(), answer.authorName(),
                    answer.parentCommentId(), answer.body(), answer.isOfficialAnswer(),
                    answer.isFaq(), answer.createdAt(), answer, question);
        }
    }
    public record FaqPageResponse(List<FaqItemResponse> content, int page, int pageSize,
                                  long totalElements, int totalPages) { }

    public static class DiscussionFieldValidationException extends RuntimeException {
        private final List<ValidationErrorDetail> details;
        public DiscussionFieldValidationException(List<ValidationErrorDetail> details) {
            super("Discussion validation error");
            this.details = details;
        }
        public List<ValidationErrorDetail> getDetails() { return details; }
    }
}
