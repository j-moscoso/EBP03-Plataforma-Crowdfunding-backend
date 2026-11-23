package com.ebp03.plataforma_crowdfunding_backend.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.subscription.api.CreateSubscriptionRequest;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscription;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscriptionStatus;
import com.ebp03.plataforma_crowdfunding_backend.subscription.repository.CreatorSubscriptionRepository;
import com.ebp03.plataforma_crowdfunding_backend.subscription.service.SubscriptionService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CreatorSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void firstSuccessfulChargeCreatesSubscriptionContributionPaymentAndReceipt() throws Exception {
        Cookie sponsorSession = register("subscription-first-sponsor@test.local", "sponsor");
        Cookie creatorSession = register("subscription-first-creator@test.local", "creator");
        User creator = findUser("subscription-first-creator@test.local");

        MvcResult result = create(sponsorSession, creator.getId(), "25.00", "usd", "sim_success")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creatorId").value(creator.getId().toString()))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.paymentMethodRef").doesNotExist())
                .andReturn();

        UUID subscriptionId = UUID.fromString(jsonValue(result, "id"));
        CreatorSubscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        Instant expectedFirstRenewal = subscription.getLastChargeAt().atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        assertEquals(expectedFirstRenewal, subscription.getNextChargeAt());
        assertEquals(1, count("select count(*) from contributions where subscription_id = ?", subscriptionId));
        assertEquals(1, count("select count(*) from subscription_charge_attempts where subscription_id = ?", subscriptionId));
        assertEquals(1, count("""
                select count(*) from payments p
                join subscription_charge_attempts a on a.id = p.subscription_charge_attempt_id
                where a.subscription_id = ? and p.status = 'SUCCEEDED'
                """, subscriptionId));
        assertEquals(1, count("""
                select count(*) from transaction_receipts r
                join contributions c on c.id = r.contribution_id
                where c.subscription_id = ? and r.status = 'SUCCESSFUL'
                """, subscriptionId));
        assertEquals(1, count("""
                select count(*) from subscription_charge_attempts
                where subscription_id = ? and status = 'SUCCEEDED'
                """, subscriptionId));

        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].campaignId").doesNotExist())
                .andExpect(jsonPath("$.content[0].creatorId").value(creator.getId().toString()))
                .andExpect(jsonPath("$.content[0].subscriptionId").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.content[0].contributionStatus").value("CONFIRMED"));
        mockMvc.perform(get("/api/subscriptions/me").cookie(sponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(subscriptionId.toString()));
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void invalidFirstPaymentAndInputsDoNotActivateSubscription() throws Exception {
        Cookie sponsorSession = register("subscription-invalid-sponsor@test.local", "sponsor");
        Cookie creatorSession = register("subscription-invalid-creator@test.local", "creator");
        User creator = findUser("subscription-invalid-creator@test.local");

        create(sponsorSession, creator.getId(), "10.00", "USD", "sim_failure")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_INSUFFICIENT_FUNDS"));
        create(sponsorSession, creator.getId(), "10.00", "USD", "not-a-provider-reference")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_INVALID"));
        create(sponsorSession, creator.getId(), "0", "USD", "sim_success")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(sponsorSession, creator.getId(), "10.123", "USD", "sim_success")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(sponsorSession, creator.getId(), "10.00", "US", "sim_success")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/subscriptions")
                        .cookie(sponsorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":\"" + creator.getId()
                                + "\",\"amount\":10,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(sponsorSession, UUID.randomUUID(), "10.00", "USD", "sim_success")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
        create(creatorSession, creator.getId(), "10.00", "USD", "sim_success")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPONSOR_ROLE_REQUIRED"));
        mockMvc.perform(get("/api/subscriptions/me").cookie(creatorSession))
                .andExpect(status().isForbidden());

        User inactiveCreator = userRepository.saveAndFlush(new User(
                "Inactive creator", "subscription-inactive-creator@test.local", "hashed",
                UserRole.CREATOR, VerificationStatus.VERIFIED, AccountStatus.DISABLED));
        create(sponsorSession, inactiveCreator.getId(), "10.00", "USD", "sim_success")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_ACTIVE"));
        User sponsor = findUser("subscription-invalid-sponsor@test.local");
        create(sponsorSession, sponsor.getId(), "10.00", "USD", "sim_success")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_SUBSCRIPTION_NOT_ALLOWED"));

        assertEquals(0, count("select count(*) from creator_subscriptions where sponsor_id = ?", sponsor.getId()));
        assertEquals(0, count("select count(*) from contributions where sponsor_id = ?", sponsor.getId()));
        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancellationIsOwnerOnlyIdempotentAndStopsRenewals() throws Exception {
        Cookie ownerSession = register("subscription-cancel-owner@test.local", "sponsor");
        Cookie otherSponsorSession = register("subscription-cancel-other@test.local", "sponsor");
        Cookie creatorSession = register("subscription-cancel-creator@test.local", "creator");
        User creator = findUser("subscription-cancel-creator@test.local");
        UUID subscriptionId = createAndGetId(ownerSession, creator.getId());

        mockMvc.perform(get("/api/subscriptions/me").cookie(otherSponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/subscriptions/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/subscriptions/{id}/cancel", subscriptionId).cookie(otherSponsorSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_OWNER_REQUIRED"));
        mockMvc.perform(post("/api/subscriptions/{id}/cancel", subscriptionId).cookie(creatorSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/subscriptions/{id}/cancel", subscriptionId).cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        Instant cancelledAt = subscriptionRepository.findById(subscriptionId).orElseThrow().getCancelledAt();
        mockMvc.perform(post("/api/subscriptions/{id}/cancel", subscriptionId).cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(cancelledAt, subscriptionRepository.findById(subscriptionId).orElseThrow().getCancelledAt());

        Instant pastDue = Instant.now().minusSeconds(86_400);
        jdbcTemplate.update("update creator_subscriptions set next_charge_at = ? where id = ?", pastDue, subscriptionId);
        assertEquals(0, subscriptionService.processDueSubscriptions(Instant.now()));
        assertEquals(1, count("select count(*) from contributions where subscription_id = ?", subscriptionId));
    }

    @Test
    void successfulAndFailedRenewalsAreIdempotentAndHaveDistinctHistory() throws Exception {
        Cookie sponsorSession = register("subscription-renew-sponsor@test.local", "sponsor");
        Cookie creatorSession = register("subscription-renew-creator@test.local", "creator");
        User creator = findUser("subscription-renew-creator@test.local");
        UUID successfulId = createAndGetId(sponsorSession, creator.getId());
        CreatorSubscription successful = subscriptionRepository.findById(successfulId).orElseThrow();
        assertEquals(0, subscriptionService.processDueSubscriptions(Instant.now()));
        Instant processingTime = successful.getNextChargeAt().plusSeconds(5);

        assertEquals(1, subscriptionService.processDueSubscriptions(processingTime));
        assertEquals(0, subscriptionService.processDueSubscriptions(processingTime));
        successful = subscriptionRepository.findById(successfulId).orElseThrow();
        assertEquals(processingTime, successful.getLastChargeAt());
        assertEquals(processingTime.atZone(ZoneOffset.UTC).plusMonths(1).toInstant(), successful.getNextChargeAt());
        assertEquals(2, count("select count(*) from contributions where subscription_id = ?", successfulId));
        assertEquals(2, count("""
                select count(*) from transaction_receipts r
                join contributions c on c.id = r.contribution_id
                where c.subscription_id = ? and r.status = 'SUCCESSFUL'
                """, successfulId));
        assertEquals(1, count("""
                select count(*) from notifications where type = 'RECURRING_CONTRIBUTION_CONFIRMED'
                  and contribution_id in (select id from contributions where subscription_id = ?)
                """, successfulId));
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("RECURRING_CONTRIBUTION_CONFIRMED"))
                .andExpect(jsonPath("$.content[0].campaignId").doesNotExist())
                .andExpect(jsonPath("$.content[0].contributionId").isNotEmpty());

        Cookie failedSponsorSession = register("subscription-fail-renew-sponsor@test.local", "sponsor");
        register("subscription-fail-renew-creator@test.local", "creator");
        User failedCreator = findUser("subscription-fail-renew-creator@test.local");
        UUID failedId = createAndGetId(failedSponsorSession, failedCreator.getId());
        jdbcTemplate.update("update creator_subscriptions set payment_method_ref = 'sim_failure' where id = ?", failedId);
        CreatorSubscription failed = subscriptionRepository.findById(failedId).orElseThrow();
        Instant failedAt = failed.getNextChargeAt().plusSeconds(5);

        assertEquals(1, subscriptionService.processDueSubscriptions(failedAt));
        failed = subscriptionRepository.findById(failedId).orElseThrow();
        assertEquals(CreatorSubscriptionStatus.ACTIVE, failed.getStatus());
        assertEquals(failedAt.atZone(ZoneOffset.UTC).plusMonths(1).toInstant(), failed.getNextChargeAt());
        assertEquals(1, count("select count(*) from contributions where subscription_id = ?", failedId));
        assertEquals(1, count("""
                select count(*) from subscription_charge_attempts
                where subscription_id = ? and status = 'FAILED'
                """, failedId));
        assertEquals(1, count("""
                select count(*) from payments p
                join subscription_charge_attempts a on a.id = p.subscription_charge_attempt_id
                where a.subscription_id = ? and p.contribution_id is null and p.status = 'FAILED'
                """, failedId));
        assertEquals(0, count("""
                select count(*) from notifications where contribution_id in
                    (select id from contributions where subscription_id = ?)
                """, failedId));
        assertEquals(1, count("""
                select count(*) from transaction_receipts r
                join contributions c on c.id = r.contribution_id
                where c.subscription_id = ?
                """, failedId));
    }

    @Test
    void concurrentCreationForTheSameCreatorProducesOnlyOneActiveSubscription() throws Exception {
        register("subscription-race-sponsor@test.local", "sponsor");
        register("subscription-race-creator@test.local", "creator");
        User sponsor = findUser("subscription-race-sponsor@test.local");
        User creator = findUser("subscription-race-creator@test.local");
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                creator.getId(), new BigDecimal("8.00"), "USD", "sim_success");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryCreate(sponsor, request, ready, start));
            Future<Boolean> second = executor.submit(() -> tryCreate(sponsor, request, ready, start));
            ready.await();
            start.countDown();
            assertNotNull(first.get());
            assertNotNull(second.get());
            assertTrue(first.get() ^ second.get());
            assertEquals(1, subscriptionRepository.findBySponsorIdAndCreatorIdAndStatus(
                    sponsor.getId(), creator.getId(), CreatorSubscriptionStatus.ACTIVE).stream().count());
            assertEquals(1, count("select count(*) from contributions where sponsor_id = ?", sponsor.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryCreate(
            User sponsor,
            CreateSubscriptionRequest request,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            subscriptionService.create(sponsor, request);
            return true;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409) return false;
            throw exception;
        }
    }

    private org.springframework.test.web.servlet.ResultActions create(
            Cookie session, UUID creatorId, String amount, String currency, String paymentMethodId) throws Exception {
        String body = "{\"creatorId\":\"" + creatorId + "\",\"amount\":" + amount
                + ",\"currency\":\"" + currency + "\",\"paymentMethodId\":\"" + paymentMethodId + "\"}";
        return mockMvc.perform(post("/api/subscriptions")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID createAndGetId(Cookie session, UUID creatorId) throws Exception {
        MvcResult result = create(session, creatorId, "25.00", "USD", "sim_success")
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(jsonValue(result, "id"));
    }

    private Cookie register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Subscription User\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private int count(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private String jsonValue(MvcResult result, String field) throws Exception {
        String marker = "\"" + field + "\":\"";
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("Field not found: " + field);
        start += marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}
