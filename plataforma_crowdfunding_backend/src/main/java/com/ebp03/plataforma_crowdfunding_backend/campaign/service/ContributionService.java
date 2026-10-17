package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.*;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContributionService {
    private final CampaignRepository campaigns;
    private final ContributionRepository contributions;
    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final PaymentProvider paymentProvider;
    private final PaymentWebhookAdapter webhookAdapter;
    private final TransactionReceiptRepository receipts;
    private final BigDecimal feeRate;
    private final String webhookSecret;

    public ContributionService(CampaignRepository campaigns, ContributionRepository contributions,
                               PaymentRepository payments, PaymentEventRepository events,
                               PaymentProvider paymentProvider, PaymentWebhookAdapter webhookAdapter,
                               TransactionReceiptRepository receipts,
                               @Value("${app.payment.platform-fee-rate:0.05}") BigDecimal feeRate,
                               @Value("${app.payment.webhook-secret:development-webhook-secret}") String webhookSecret) {
        this.campaigns = campaigns; this.contributions = contributions; this.payments = payments;
        this.events = events; this.paymentProvider = paymentProvider; this.webhookAdapter = webhookAdapter;
        this.receipts = receipts; this.feeRate = feeRate; this.webhookSecret = webhookSecret;
    }

    @Transactional(noRollbackFor = ContributionCapacityException.class)
    public ContributionResponse create(User sponsor, UUID campaignId, CreateRequest request, String idempotencyKey) {
        requireSponsor(sponsor);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) bad("Idempotency-Key es obligatorio");
        var existing = contributions.findBySponsorIdAndIdempotencyKey(sponsor.getId(), idempotencyKey);
        if (existing.isPresent()) return response(existing.get());
        Campaign campaign = lockedActiveCampaign(campaignId);
        validateAmount(request.amount(), request.currency());
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        requireCampaignCurrency(campaign, currency);
        validateRemaining(campaign, request.amount());
        CampaignReward reward = findReward(campaign, request.rewardId());
        if (reward != null) {
            if (request.amount().compareTo(reward.getMinimumAmount()) < 0) bad("El monto no alcanza el mínimo de la recompensa");
            if (reward.getQuantity() != null && reward.getClaimedQuantity() >= reward.getQuantity()) bad("La recompensa no está disponible");
        }
        if (request.paymentMethodId() == null || request.paymentMethodId().isBlank()) bad("paymentMethodId es obligatorio");

        Contribution contribution = contributions.save(new Contribution(campaign, sponsor.getId(), reward,
                request.amount().setScale(2), currency, ContributionStatus.PENDING, idempotencyKey));
        Payment payment = payments.save(new Payment(contribution, "simulated", contribution.getAmount(),
                contribution.getAmount().multiply(feeRate).setScale(2, RoundingMode.HALF_UP)));
        applyProviderResult(contribution, payment, paymentProvider.charge(request.paymentMethodId(), contribution.getAmount(), contribution.getCurrency()));
        return response(contribution);
    }

    @Transactional(noRollbackFor = ContributionCapacityException.class)
    public ContributionResponse retry(User sponsor, UUID contributionId, String paymentMethodId, String idempotencyKey) {
        requireSponsor(sponsor);
        if (idempotencyKey == null || idempotencyKey.isBlank()) bad("Idempotency-Key es obligatorio");
        Contribution contribution = ownedContribution(sponsor, contributionId);
        if (contribution.getStatus() == ContributionStatus.CONFIRMED) return response(contribution);
        if (contribution.getStatus() == ContributionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONTRIBUTION_ALREADY_REFUNDED");
        }
        Campaign campaign = lockedActiveCampaign(contribution.getCampaign().getId());
        requireCampaignCurrency(campaign, contribution.getCurrency());
        validateRemaining(campaign, contribution.getAmount());
        if (contribution.getReward() != null && contribution.getReward().getQuantity() != null
                && contribution.getReward().getClaimedQuantity() >= contribution.getReward().getQuantity()) bad("La recompensa no está disponible");
        contribution.setStatus(ContributionStatus.PENDING);
        Payment payment = payments.save(new Payment(contribution, "simulated", contribution.getAmount(),
                contribution.getAmount().multiply(feeRate).setScale(2, RoundingMode.HALF_UP)));
        applyProviderResult(contribution, payment, paymentProvider.charge(paymentMethodId, contribution.getAmount(), contribution.getCurrency()));
        return response(contribution);
    }

    @Transactional(readOnly = true)
    public ContributionResponse detail(User user, UUID id) {
        Contribution contribution = contributions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aporte no encontrado"));
        if (user == null || (user.getRole() != UserRole.SPONSOR || !contribution.getSponsorId().equals(user.getId()))
                && (user.getRole() != UserRole.CREATOR || !contribution.getCampaign().getCreatorId().equals(user.getId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para ver este aporte");
        return response(contribution);
    }

    @Transactional(noRollbackFor = ContributionCapacityException.class)
    public void webhook(String signature, String payload, String providerEventId, UUID paymentId, String eventType, String status) {
        if (payload == null || providerEventId == null || providerEventId.isBlank() || providerEventId.length() > 160
                || providerEventId.contains("\r") || providerEventId.contains("\n") || paymentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        PaymentStatus targetStatus = webhookAdapter.toPaymentStatus(eventType, status);
        String expectedSignature = sign(payload, providerEventId, paymentId, eventType, status);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma inválida");
        }
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND"));
        if (events.findByProviderEventId(providerEventId).isPresent()) return;
        PaymentEvent event = events.save(new PaymentEvent(payment, providerEventId, eventType, sha256(payload)));
        try {
            applyWebhookStatus(payment, targetStatus);
        } catch (ContributionCapacityException exception) {
            failForCapacity(payment.getContribution(), payment, exception);
            payments.save(payment);
            contributions.save(payment.getContribution());
            event.setProcessedAt(Instant.now());
            throw exception;
        }
        event.setProcessedAt(Instant.now());
    }

    public String sign(String payload, String providerEventId, UUID paymentId, String eventType, String status) {
        if (payload == null || providerEventId == null || paymentId == null || eventType == null || status == null) {
            throw new IllegalArgumentException("Webhook signature fields are required");
        }
        String canonical = signatureField(payload)
                + signatureField(providerEventId)
                + signatureField(paymentId.toString())
                + signatureField(eventType)
                + signatureField(status);
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private String signatureField(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length + ":" + value;
    }

    private void applyProviderResult(Contribution contribution, Payment payment, PaymentProvider.Result result) {
        if (result.successful()) {
            try {
                confirm(contribution);
                payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId());
                createOrUpdateReceipt(contribution, TransactionReceiptStatus.SUCCESSFUL);
            } catch (ContributionCapacityException exception) {
                failForCapacity(contribution, payment, exception);
                payments.save(payment);
                contributions.save(contribution);
                throw exception;
            }
        } else {
            markFailed(contribution, payment, result.failureCode());
        }
        payments.save(payment); contributions.save(contribution);
    }

    private void applyWebhookStatus(Payment payment, PaymentStatus targetStatus) {
        Contribution contribution = payment.getContribution();
        switch (targetStatus) {
            case PROCESSING -> {
                if (payment.getStatus() == PaymentStatus.CREATED) payment.setStatus(PaymentStatus.PROCESSING);
            }
            case SUCCEEDED -> {
                if (payment.getStatus() != PaymentStatus.REFUNDED) {
                    markSuccessful(contribution, payment, payment.getProviderPaymentId());
                }
            }
            case FAILED -> markFailed(contribution, payment, "PROVIDER_FAILURE");
            case REFUNDED -> markRefunded(contribution, payment);
            case CREATED -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        payments.save(payment);
        contributions.save(contribution);
    }

    private void markSuccessful(Contribution contribution, Payment payment, String providerPaymentId) {
        if (payment.getStatus() == PaymentStatus.REFUNDED
                || contribution.getStatus() == ContributionStatus.REFUNDED) {
            return;
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        if (providerPaymentId != null) payment.setProviderPaymentId(providerPaymentId);
        if (isLatestPayment(payment) && contribution.getStatus() != ContributionStatus.CONFIRMED) {
            confirm(contribution);
            createOrUpdateReceipt(contribution, TransactionReceiptStatus.SUCCESSFUL);
        } else if (isLatestPayment(payment)) {
            createOrUpdateReceipt(contribution, TransactionReceiptStatus.SUCCESSFUL);
        }
    }

    private void markFailed(Contribution contribution, Payment payment, String failureCode) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailure(failureCode, "Payment provider failure");
        if (isLatestPayment(payment)
                && contribution.getStatus() != ContributionStatus.CONFIRMED
                && contribution.getStatus() != ContributionStatus.REFUNDED) {
            contribution.setStatus(ContributionStatus.FAILED);
        }
    }

    private void markRefunded(Contribution contribution, Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) return;
        payment.setStatus(PaymentStatus.REFUNDED);
        if (isLatestPayment(payment)) {
            contribution.setStatus(ContributionStatus.REFUNDED);
            receipts.findByContributionId(contribution.getId())
                    .ifPresent(receipt -> receipt.setStatus(TransactionReceiptStatus.REFUNDED));
        }
    }

    private boolean isLatestPayment(Payment payment) {
        return payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(payment.getContribution().getId())
                .map(latest -> latest.getId().equals(payment.getId()))
                .orElse(false);
    }

    private void createOrUpdateReceipt(Contribution contribution, TransactionReceiptStatus status) {
        TransactionReceipt receipt = receipts.findByContributionId(contribution.getId())
                .orElseGet(() -> new TransactionReceipt(
                        contribution,
                        "REF-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT),
                        status));
        receipt.setStatus(status);
        receipts.save(receipt);
    }

    private void confirm(Contribution contribution) {
        if (contribution.getStatus() == ContributionStatus.CONFIRMED) return;
        Campaign campaign = lockedActiveCampaign(contribution.getCampaign().getId());
        validateRemaining(campaign, contribution.getAmount());
        contribution.setStatus(ContributionStatus.CONFIRMED); contribution.setConfirmedAt(Instant.now());
        CampaignReward reward = contribution.getReward();
        if (reward != null) reward.setClaimedQuantity(reward.getClaimedQuantity() + 1);
    }

    private void failForCapacity(Contribution contribution, Payment payment,
                                 ContributionCapacityException exception) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailure(exception.getReason(), "El aporte ya no cabe en el restante de la campaña.");
        if (contribution.getStatus() != ContributionStatus.CONFIRMED
                && contribution.getStatus() != ContributionStatus.REFUNDED) {
            contribution.setStatus(ContributionStatus.FAILED);
        }
    }

    private Campaign activeCampaign(UUID id) {
        Campaign campaign = campaigns.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (campaign.getStatus() != CampaignStatus.ACTIVE || !campaign.getDeadline().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAIGN_NOT_ACTIVE");
        return campaign;
    }
    private void requireCampaignCurrency(Campaign campaign, String currency) {
        if (campaign.getCurrency() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAIGN_CURRENCY_UNAVAILABLE");
        }
        if (!campaign.getCurrency().equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAIGN_CURRENCY_MISMATCH");
        }
    }

    private Campaign lockedActiveCampaign(UUID id) {
        Campaign campaign = campaigns.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (campaign.getStatus() != CampaignStatus.ACTIVE || !campaign.getDeadline().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAIGN_NOT_ACTIVE");
        return campaign;
    }

    private void validateRemaining(Campaign campaign, BigDecimal amount) {
        BigDecimal confirmed = contributions.sumAmountByCampaignIdAndStatus(campaign.getId(), ContributionStatus.CONFIRMED);
        if (confirmed == null) confirmed = BigDecimal.ZERO;
        BigDecimal remaining = campaign.getGoalAmount().subtract(confirmed).max(BigDecimal.ZERO);
        if (amount.compareTo(remaining) > 0)
            throw new ContributionCapacityException();
    }
    
    private CampaignReward findReward(Campaign campaign, UUID rewardId) {
        if (rewardId == null) return null;
        return campaign.getRewards().stream().filter(reward -> reward.getId().equals(rewardId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La recompensa no pertenece a la campaña"));
    }
    private Contribution ownedContribution(User sponsor, UUID id) {
        Contribution c = contributions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aporte no encontrado"));
        if (!c.getSponsorId().equals(sponsor.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para reintentar este aporte");
        return c;
    }
    private ContributionResponse response(Contribution c) { Payment p = payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(c.getId()).orElse(null); UUID rewardId = c.getReward() == null ? null : c.getReward().getId(); return new ContributionResponse(c.getId(), c.getCampaign().getId(), c.getSponsorId(), rewardId, c.getAmount(), c.getCurrency(), c.getStatus(), p == null ? null : new PaymentResponse(p.getId(), p.getStatus()), c.getCreatedAt(), new ContributionView(c.getId(), c.getCampaign().getId(), c.getSponsorId(), rewardId, c.getAmount(), c.getCurrency(), c.getStatus())); }
    private void requireSponsor(User user) { if (user == null || user.getRole() != UserRole.SPONSOR) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los sponsors pueden realizar aportes"); }
    private void validateAmount(BigDecimal amount, String currency) { if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > 2) bad("amount debe ser positivo con máximo dos decimales"); if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) bad("currency debe tener 3 letras"); }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }

    public record CreateRequest(BigDecimal amount, String currency, UUID rewardId, String paymentMethodId) { }
    public record RetryRequest(String paymentMethodId) { }
    public record PaymentResponse(UUID id, PaymentStatus status) { }
    public record ContributionResponse(UUID id, UUID campaignId, UUID sponsorId, UUID rewardId, BigDecimal amount, String currency, ContributionStatus status, PaymentResponse payment, Instant createdAt, ContributionView contribution) { }
    public record ContributionView(UUID id, UUID campaignId, UUID sponsorId, UUID rewardId, BigDecimal amount, String currency, ContributionStatus status) { }
    public record PageResponse(java.util.List<ContributionResponse> content, int page, int pageSize, long totalElements, int totalPages) { }

    public static class ContributionCapacityException extends ResponseStatusException {
        public ContributionCapacityException() {
            super(HttpStatus.BAD_REQUEST, "CONTRIBUTION_EXCEEDS_REMAINING_AMOUNT");
        }
    }
}