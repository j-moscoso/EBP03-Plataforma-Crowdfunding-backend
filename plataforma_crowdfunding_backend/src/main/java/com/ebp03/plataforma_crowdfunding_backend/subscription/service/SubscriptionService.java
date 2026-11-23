package com.ebp03.plataforma_crowdfunding_backend.subscription.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Notification;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.NotificationRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceipt;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.TransactionReceiptRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.PaymentProvider;
import com.ebp03.plataforma_crowdfunding_backend.subscription.api.CreateSubscriptionRequest;
import com.ebp03.plataforma_crowdfunding_backend.subscription.api.SubscriptionPageResponse;
import com.ebp03.plataforma_crowdfunding_backend.subscription.api.SubscriptionResponse;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscription;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscriptionStatus;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.SubscriptionChargeAttempt;
import com.ebp03.plataforma_crowdfunding_backend.subscription.repository.CreatorSubscriptionRepository;
import com.ebp03.plataforma_crowdfunding_backend.subscription.repository.SubscriptionChargeAttemptRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionService {
    private static final String RENEWAL_NOTIFICATION_TYPE = "RECURRING_CONTRIBUTION_CONFIRMED";
    private static final int MAX_RENEWALS_PER_BATCH = 20;

    private final UserRepository users;
    private final CreatorSubscriptionRepository subscriptions;
    private final SubscriptionChargeAttemptRepository attempts;
    private final ContributionRepository contributions;
    private final PaymentRepository payments;
    private final TransactionReceiptRepository receipts;
    private final NotificationRepository notifications;
    private final PaymentProvider paymentProvider;
    private final BigDecimal feeRate;

    public SubscriptionService(
            UserRepository users,
            CreatorSubscriptionRepository subscriptions,
            SubscriptionChargeAttemptRepository attempts,
            ContributionRepository contributions,
            PaymentRepository payments,
            TransactionReceiptRepository receipts,
            NotificationRepository notifications,
            PaymentProvider paymentProvider,
            @Value("${app.payment.platform-fee-rate:0.05}") BigDecimal feeRate) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.attempts = attempts;
        this.contributions = contributions;
        this.payments = payments;
        this.receipts = receipts;
        this.notifications = notifications;
        this.paymentProvider = paymentProvider;
        this.feeRate = feeRate;
    }

    @Transactional
    public SubscriptionResponse create(User authenticatedSponsor, CreateSubscriptionRequest request) {
        User sponsor = requireSponsor(authenticatedSponsor);
        String currency = validateAndNormalize(request);
        if (sponsor.getId().equals(request.creatorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SELF_SUBSCRIPTION_NOT_ALLOWED");
        }

        User creator = users.findByIdForUpdate(request.creatorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND"));
        if (creator.getRole() != UserRole.CREATOR || creator.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CREATOR_NOT_ACTIVE");
        }
        if (subscriptions.findBySponsorIdAndCreatorIdAndStatus(
                sponsor.getId(), creator.getId(), CreatorSubscriptionStatus.ACTIVE).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBSCRIPTION_ALREADY_ACTIVE");
        }

        Instant scheduledAt = Instant.now();
        CreatorSubscription subscription = new CreatorSubscription(
                sponsor.getId(), creator.getId(), request.paymentMethodId(), request.amount().setScale(2),
                currency, nextMonth(scheduledAt));
        String idempotencyKey = idempotencyKey(subscription.getId(), scheduledAt);
        PaymentProvider.Result result = paymentProvider.charge(
                request.paymentMethodId(), subscription.getAmount(), subscription.getCurrency());
        if (!result.successful()) {
            throw paymentFailure(result.failureCode());
        }

        Instant chargedAt = Instant.now();
        subscription.completeCharge(chargedAt, nextMonth(chargedAt));
        subscription = subscriptions.save(subscription);

        SubscriptionChargeAttempt attempt = attempts.save(new SubscriptionChargeAttempt(
                subscription, scheduledAt, idempotencyKey));
        Contribution contribution = contributions.save(new Contribution(
                sponsor.getId(), creator.getId(), subscription.getId(), subscription.getAmount(),
                subscription.getCurrency(), ContributionStatus.CONFIRMED, idempotencyKey));
        contribution.setConfirmedAt(chargedAt);

        Payment payment = new Payment(
                contribution, "simulated", subscription.getAmount(), platformFee(subscription.getAmount()),
                attempt.getId());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderPaymentId(result.providerPaymentId());
        payment = payments.save(payment);

        attempt.markSucceeded(contribution, payment, chargedAt);
        attempts.save(attempt);
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.SUCCESSFUL));

        return SubscriptionResponse.from(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionPageResponse getMine(User authenticatedSponsor, int page, int pageSize) {
        User sponsor = requireSponsor(authenticatedSponsor);
        validatePagination(page, pageSize);
        Page<CreatorSubscription> result = subscriptions.findBySponsorId(
                sponsor.getId(),
                PageRequest.of(page, pageSize, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new SubscriptionPageResponse(
                result.getContent().stream().map(SubscriptionResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SubscriptionResponse cancel(User authenticatedSponsor, UUID subscriptionId) {
        User sponsor = requireSponsor(authenticatedSponsor);
        CreatorSubscription subscription = subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND"));
        if (!subscription.getSponsorId().equals(sponsor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUBSCRIPTION_OWNER_REQUIRED");
        }
        if (subscription.getStatus() == CreatorSubscriptionStatus.CANCELLED) {
            return SubscriptionResponse.from(subscription);
        }
        if (subscription.getStatus() != CreatorSubscriptionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBSCRIPTION_NOT_ACTIVE");
        }
        subscription.cancel(Instant.now());
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public int processDueSubscriptions(Instant now) {
        if (now == null) throw new IllegalArgumentException("The renewal processing instant is required");
        List<UUID> dueIds = subscriptions.findDueIds(
                CreatorSubscriptionStatus.ACTIVE, now, PageRequest.of(0, MAX_RENEWALS_PER_BATCH));
        int processed = 0;
        for (UUID subscriptionId : dueIds) {
            CreatorSubscription subscription = subscriptions.findByIdForUpdate(subscriptionId).orElse(null);
            if (subscription == null
                    || subscription.getStatus() != CreatorSubscriptionStatus.ACTIVE
                    || subscription.getNextChargeAt().isAfter(now)) {
                continue;
            }

            Instant scheduledAt = subscription.getNextChargeAt();
            String idempotencyKey = idempotencyKey(subscription.getId(), scheduledAt);
            if (attempts.findByIdempotencyKey(idempotencyKey).isPresent()) {
                continue;
            }

            SubscriptionChargeAttempt attempt = attempts.save(new SubscriptionChargeAttempt(
                    subscription, scheduledAt, idempotencyKey));
            PaymentProvider.Result result = paymentProvider.charge(
                    subscription.getPaymentMethodRef(), subscription.getAmount(), subscription.getCurrency());
            if (result.successful()) {
                completeSuccessfulRenewal(subscription, attempt, result, idempotencyKey, now);
            } else {
                recordFailedRenewal(subscription, attempt, result.failureCode(), now);
            }
            processed++;
        }
        return processed;
    }

    private void completeSuccessfulRenewal(
            CreatorSubscription subscription,
            SubscriptionChargeAttempt attempt,
            PaymentProvider.Result result,
            String idempotencyKey,
            Instant chargedAt) {
        Contribution contribution = contributions.save(new Contribution(
                subscription.getSponsorId(), subscription.getCreatorId(), subscription.getId(),
                subscription.getAmount(), subscription.getCurrency(), ContributionStatus.CONFIRMED,
                idempotencyKey));
        contribution.setConfirmedAt(chargedAt);
        Payment payment = new Payment(
                contribution, "simulated", subscription.getAmount(), platformFee(subscription.getAmount()),
                attempt.getId());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderPaymentId(result.providerPaymentId());
        payment = payments.save(payment);

        attempt.markSucceeded(contribution, payment, chargedAt);
        attempts.save(attempt);
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.SUCCESSFUL));
        createRenewalNotification(subscription, contribution);
        subscription.completeCharge(chargedAt, nextMonth(chargedAt));
    }

    private void recordFailedRenewal(
            CreatorSubscription subscription,
            SubscriptionChargeAttempt attempt,
            String providerFailureCode,
            Instant processedAt) {
        String failureCode = safeFailureCode(providerFailureCode);
        Payment payment = new Payment(
                "simulated", subscription.getAmount(), platformFee(subscription.getAmount()), attempt.getId());
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailure(failureCode, "No se pudo completar el cobro mensual.");
        payment = payments.save(payment);

        attempt.markFailed(payment, processedAt, failureCode, "No se pudo completar el cobro mensual.");
        attempts.save(attempt);
        subscription.scheduleNextCharge(nextMonth(processedAt));
    }

    private void createRenewalNotification(CreatorSubscription subscription, Contribution contribution) {
        if (notifications.existsByContributionIdAndType(contribution.getId(), RENEWAL_NOTIFICATION_TYPE)) {
            return;
        }
        String sponsorName = users.findById(subscription.getSponsorId())
                .map(User::getName)
                .filter(name -> !name.isBlank())
                .orElse("Anónimo");
        String body = "El patrocinador " + sponsorName + " realizó un aporte mensual de "
                + subscription.getAmount().toPlainString() + " " + subscription.getCurrency() + ".";
        notifications.save(new Notification(
                subscription.getCreatorId(), null, null, contribution.getId(),
                RENEWAL_NOTIFICATION_TYPE, "Aporte recurrente recibido", body));
    }

    private BigDecimal platformFee(BigDecimal amount) {
        return amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    }

    private String validateAndNormalize(CreateSubscriptionRequest request) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        if (request == null) {
            throw new ValidationException(List.of(new ValidationErrorDetail(
                    "body", "REQUIRED", "El cuerpo de la solicitud es obligatorio.")));
        }
        if (request.creatorId() == null) {
            errors.add(new ValidationErrorDetail("creatorId", "REQUIRED", "El creador es obligatorio."));
        }
        BigDecimal amount = request.amount();
        if (amount == null) {
            errors.add(new ValidationErrorDetail("amount", "REQUIRED", "El monto es obligatorio."));
        } else if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            errors.add(new ValidationErrorDetail(
                    "amount", "INVALID_AMOUNT", "El monto debe ser positivo y tener máximo dos decimales."));
        }
        String currency = request.currency() == null ? "" : request.currency().trim().toUpperCase(Locale.ROOT);
        if (currency.isEmpty()) {
            errors.add(new ValidationErrorDetail("currency", "REQUIRED", "La moneda es obligatoria."));
        } else if (!currency.matches("[A-Z]{3}")) {
            errors.add(new ValidationErrorDetail(
                    "currency", "INVALID_FORMAT", "La moneda debe contener tres letras."));
        }
        String paymentMethodId = request.paymentMethodId();
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            errors.add(new ValidationErrorDetail(
                    "paymentMethodId", "REQUIRED", "El método de pago es obligatorio."));
        } else if (paymentMethodId.length() > 160) {
            errors.add(new ValidationErrorDetail(
                    "paymentMethodId", "LENGTH", "La referencia de pago excede el máximo permitido."));
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return currency;
    }

    private User requireSponsor(User user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (user.getRole() != UserRole.SPONSOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SPONSOR_ROLE_REQUIRED");
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SPONSOR_ACCOUNT_INACTIVE");
        }
        return user;
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION");
        }
    }

    private ResponseStatusException paymentFailure(String failureCode) {
        return new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, safeFailureCode(failureCode));
    }

    private String safeFailureCode(String failureCode) {
        return "PAYMENT_METHOD_INSUFFICIENT_FUNDS".equals(failureCode)
                || "SIMULATED_FAILURE".equals(failureCode)
                ? "PAYMENT_METHOD_INSUFFICIENT_FUNDS"
                : "PAYMENT_METHOD_INVALID";
    }

    private Instant nextMonth(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    private String idempotencyKey(UUID subscriptionId, Instant scheduledAt) {
        return "subscription:" + subscriptionId + ":charge:" + scheduledAt.getEpochSecond();
    }

    public static class ValidationException extends RuntimeException {
        private final List<ValidationErrorDetail> errors;

        public ValidationException(List<ValidationErrorDetail> errors) {
            super("Subscription request validation failed");
            this.errors = List.copyOf(errors);
        }

        public List<ValidationErrorDetail> getErrors() {
            return errors;
        }
    }
}
