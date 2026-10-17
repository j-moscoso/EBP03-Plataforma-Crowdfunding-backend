package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceipt;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.TransactionReceiptRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionHistoryService {
    private static final String EMPTY_MESSAGE = "Aún no has realizado aportes";
    private final ContributionRepository contributions;
    private final PaymentRepository payments;
    private final TransactionReceiptRepository receipts;

    public TransactionHistoryService(ContributionRepository contributions,
                                    PaymentRepository payments,
                                    TransactionReceiptRepository receipts) {
        this.contributions = contributions;
        this.payments = payments;
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public HistoryPageResponse getHistory(User authenticatedUser, int page, int pageSize, String status) {
        requireSponsor(authenticatedUser);
        validatePagination(page, pageSize);
        ContributionStatus filter = parseStatus(status);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Contribution> result = filter == null
                ? contributions.findBySponsorId(authenticatedUser.getId(), pageable)
                : contributions.findBySponsorIdAndStatus(authenticatedUser.getId(), filter, pageable);

        List<UUID> contributionIds = result.getContent().stream().map(Contribution::getId).toList();
        Map<UUID, Payment> latestPayments = payments.findByContributionIdInOrderByCreatedAtDescIdDesc(contributionIds)
                .stream()
                .collect(Collectors.toMap(
                        payment -> payment.getContribution().getId(),
                        Function.identity(),
                        (first, ignored) -> first));
        Map<UUID, TransactionReceipt> receiptByContribution = receipts.findByContributionIdIn(contributionIds)
                .stream()
                .collect(Collectors.toMap(
                        receipt -> receipt.getContribution().getId(),
                        Function.identity()));

        List<HistoryItem> content = result.getContent().stream()
                .map(contribution -> toHistoryItem(
                        contribution,
                        latestPayments.get(contribution.getId()),
                        receiptByContribution.get(contribution.getId())))
                .toList();
        return new HistoryPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                content.isEmpty() ? EMPTY_MESSAGE : null);
    }

    private HistoryItem toHistoryItem(Contribution contribution, Payment payment, TransactionReceipt receipt) {
        TransactionReceiptStatus receiptStatus = receipt == null ? null : receipt.getStatus();
        return new HistoryItem(
                contribution.getId(),
                contribution.getCreatedAt(),
                contribution.getCampaign().getId(),
                contribution.getCampaign().getTitle(),
                contribution.getAmount(),
                contribution.getCurrency(),
                receipt == null ? null : receipt.getReferenceCode(),
                receiptStatus,
                receiptStatus == null ? null : label(receiptStatus),
                contribution.getStatus(),
                payment == null ? null : payment.getStatus());
    }

    private String label(TransactionReceiptStatus status) {
        return switch (status) {
            case HELD -> "Retenido";
            case SUCCESSFUL -> "Exitoso";
            case REFUNDED -> "Reembolsado";
            case FAILED -> "Fallido";
        };
    }

    private ContributionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ContributionStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
    }

    private void requireSponsor(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (user.getRole() != UserRole.SPONSOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SPONSOR_ROLE_REQUIRED");
        }
    }

    public record HistoryItem(
            UUID contributionId,
            Instant date,
            UUID campaignId,
            String projectName,
            BigDecimal amount,
            String currency,
            String referenceCode,
            TransactionReceiptStatus receiptStatus,
            String receiptStatusLabel,
            ContributionStatus contributionStatus,
            PaymentStatus paymentStatus) {
    }

    public record HistoryPageResponse(
            List<HistoryItem> content,
            int page,
            int pageSize,
            long totalElements,
            int totalPages,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String emptyMessage) {
    }
}
