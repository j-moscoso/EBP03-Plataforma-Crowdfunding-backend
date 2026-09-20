package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.*;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
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
    private final BigDecimal feeRate;
    private final String webhookSecret;

    public ContributionService(CampaignRepository campaigns, ContributionRepository contributions,
                               PaymentRepository payments, PaymentEventRepository events,
                               PaymentProvider paymentProvider,
                               @Value("${app.payment.platform-fee-rate:0.05}") BigDecimal feeRate,
                               @Value("${app.payment.webhook-secret:development-webhook-secret}") String webhookSecret) {
        this.campaigns = campaigns; this.contributions = contributions; this.payments = payments;
        this.events = events; this.paymentProvider = paymentProvider; this.feeRate = feeRate; this.webhookSecret = webhookSecret;
    }

    @Transactional
    public ContributionResponse create(User sponsor, UUID campaignId, CreateRequest request, String idempotencyKey) {
        requireSponsor(sponsor);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) bad("Idempotency-Key es obligatorio");
        var existing = contributions.findBySponsorIdAndIdempotencyKey(sponsor.getId(), idempotencyKey);
        if (existing.isPresent()) return response(existing.get());
        Campaign campaign = activeCampaign(campaignId);
        validateAmount(request.amount(), request.currency());
        CampaignReward reward = findReward(campaign, request.rewardId());
        if (reward != null) {
            if (request.amount().compareTo(reward.getMinimumAmount()) < 0) bad("El monto no alcanza el mínimo de la recompensa");
            if (reward.getQuantity() != null && reward.getClaimedQuantity() >= reward.getQuantity()) bad("La recompensa no está disponible");
        }
        if (request.paymentMethodId() == null || request.paymentMethodId().isBlank()) bad("paymentMethodId es obligatorio");

        Contribution contribution = contributions.save(new Contribution(campaign, sponsor.getId(), reward,
                request.amount().setScale(2), request.currency().trim().toUpperCase(), ContributionStatus.PENDING, idempotencyKey));
        Payment payment = payments.save(new Payment(contribution, "simulated", contribution.getAmount(),
                contribution.getAmount().multiply(feeRate).setScale(2, RoundingMode.HALF_UP)));
        applyProviderResult(contribution, payment, paymentProvider.charge(request.paymentMethodId(), contribution.getAmount(), contribution.getCurrency()));
        return response(contribution);
    }

    @Transactional
    public ContributionResponse retry(User sponsor, UUID contributionId, String paymentMethodId, String idempotencyKey) {
        requireSponsor(sponsor);
        if (idempotencyKey == null || idempotencyKey.isBlank()) bad("Idempotency-Key es obligatorio");
        Contribution contribution = ownedContribution(sponsor, contributionId);
        if (contribution.getStatus() == ContributionStatus.CONFIRMED) return response(contribution);
        Campaign campaign = activeCampaign(contribution.getCampaign().getId());
        if (contribution.getReward() != null && contribution.getReward().getQuantity() != null
                && contribution.getReward().getClaimedQuantity() >= contribution.getReward().getQuantity()) bad("La recompensa no está disponible");
        contribution.setStatus(ContributionStatus.PENDING);
        Payment payment = payments.save(new Payment(contribution, "simulated", contribution.getAmount(),
                contribution.getAmount().multiply(feeRate).setScale(2, RoundingMode.HALF_UP)));
        applyProviderResult(contribution, payment, paymentProvider.charge(paymentMethodId, contribution.getAmount(), contribution.getCurrency()));
        return response(contribution);
    }

    @Transactional(readOnly = true)
    public PageResponse list(User sponsor, int page, int pageSize, String status) {
        requireSponsor(sponsor);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, pageSize)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Contribution> result;
        if (status == null || status.isBlank()) result = contributions.findBySponsorId(sponsor.getId(), pageable);
        else try { result = contributions.findBySponsorIdAndStatus(sponsor.getId(), ContributionStatus.valueOf(status.toUpperCase()), pageable); }
        catch (IllegalArgumentException exception) { bad("status inválido"); return null; }
        return new PageResponse(result.getContent().stream().map(this::response).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ContributionResponse detail(User user, UUID id) {
        Contribution contribution = contributions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aporte no encontrado"));
        if (user == null || (user.getRole() != UserRole.SPONSOR || !contribution.getSponsorId().equals(user.getId()))
                && (user.getRole() != UserRole.CREATOR || !contribution.getCampaign().getCreatorId().equals(user.getId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para ver este aporte");
        return response(contribution);
    }

    @Transactional
    public void webhook(String signature, String payload, String providerEventId, UUID paymentId, String eventType, String status) {
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma inválida");
        if (events.findByProviderEventId(providerEventId).isPresent()) return;
        Payment payment = paymentId == null ? null : payments.findById(paymentId).orElse(null);
        PaymentEvent event = events.save(new PaymentEvent(payment, providerEventId, eventType, sha256(payload)));
        if (payment != null && "succeeded".equalsIgnoreCase(status)) {
            applyProviderResult(payment.getContribution(), payment, new PaymentProvider.Result(true, payment.getProviderPaymentId(), null));
        } else if (payment != null && "failed".equalsIgnoreCase(status)) {
            applyProviderResult(payment.getContribution(), payment, new PaymentProvider.Result(false, null, "PROVIDER_FAILURE"));
        }
        event.setProcessedAt(Instant.now());
    }

    public String sign(String payload) { return sha256(webhookSecret + payload); }

    private void applyProviderResult(Contribution contribution, Payment payment, PaymentProvider.Result result) {
        if (result.successful()) {
            payment.setStatus(PaymentStatus.SUCCEEDED); payment.setProviderPaymentId(result.providerPaymentId());
            confirm(contribution);
        } else {
            payment.setStatus(PaymentStatus.FAILED); payment.setFailure(result.failureCode(), "Payment provider failure");
            contribution.setStatus(ContributionStatus.FAILED);
        }
        payments.save(payment); contributions.save(contribution);
    }

    private void confirm(Contribution contribution) {
        if (contribution.getStatus() == ContributionStatus.CONFIRMED) return;
        contribution.setStatus(ContributionStatus.CONFIRMED); contribution.setConfirmedAt(Instant.now());
        CampaignReward reward = contribution.getReward();
        if (reward != null) reward.setClaimedQuantity(reward.getClaimedQuantity() + 1);
    }

    private Campaign activeCampaign(UUID id) {
        Campaign campaign = campaigns.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (campaign.getStatus() != CampaignStatus.ACTIVE || !campaign.getDeadline().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CAMPAIGN_NOT_ACTIVE");
        return campaign;
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
    private ContributionResponse response(Contribution c) { Payment p = payments.findFirstByContributionIdOrderByCreatedAtDesc(c.getId()).orElse(null); UUID rewardId = c.getReward() == null ? null : c.getReward().getId(); return new ContributionResponse(c.getId(), c.getCampaign().getId(), c.getSponsorId(), rewardId, c.getAmount(), c.getCurrency(), c.getStatus(), p == null ? null : new PaymentResponse(p.getId(), p.getStatus()), c.getCreatedAt(), new ContributionView(c.getId(), c.getCampaign().getId(), c.getSponsorId(), rewardId, c.getAmount(), c.getCurrency(), c.getStatus())); }
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
}