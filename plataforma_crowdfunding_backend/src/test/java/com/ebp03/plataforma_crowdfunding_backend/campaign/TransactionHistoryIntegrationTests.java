package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceipt;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.TransactionReceiptRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.ContributionService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionHistoryIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ContributionRepository contributionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TransactionReceiptRepository receiptRepository;
    @Autowired private ContributionService contributionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void sponsorHistoryIsPrivateOrderedPagedAndIncludesCampaignAndDecimalAmount() throws Exception {
        Cookie sponsorSession = register("history-owner@test.local", "sponsor");
        Cookie otherSponsorSession = register("history-other@test.local", "sponsor");
        Cookie creatorSession = register("history-creator@test.local", "creator");
        User creator = findUser("history-creator@test.local");
        User sponsor = findUser("history-owner@test.local");
        User otherSponsor = findUser("history-other@test.local");
        Campaign campaign = campaign(creator, "Proyecto histórico");
        Contribution older = contribution(campaign, sponsor, "12.30", ContributionStatus.CONFIRMED,
                PaymentStatus.SUCCEEDED, TransactionReceiptStatus.SUCCESSFUL);
        Contribution newer = contribution(campaign, sponsor, "100.05", ContributionStatus.PENDING,
                PaymentStatus.PROCESSING, null);
        Contribution privateContribution = contribution(campaign, otherSponsor, "999.99", ContributionStatus.CONFIRMED,
                PaymentStatus.SUCCEEDED, TransactionReceiptStatus.SUCCESSFUL);

        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession)
                        .param("page", "0").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].contributionId").value(newer.getId().toString()))
                .andExpect(jsonPath("$.content[0].campaignId").value(campaign.getId().toString()))
                .andExpect(jsonPath("$.content[0].projectName").value("Proyecto histórico"))
                .andExpect(jsonPath("$.content[0].amount").value(100.05))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].referenceCode").doesNotExist())
                .andExpect(jsonPath("$.content[0].receiptStatus").doesNotExist())
                .andExpect(jsonPath("$.content[0].contributionStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.content[0].sponsorId").doesNotExist())
                .andExpect(jsonPath("$.content[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.content[0].providerPaymentId").doesNotExist())
                .andExpect(jsonPath("$.content[0].failureCode").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.emptyMessage").doesNotExist());

        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession)
                        .param("page", "0").param("pageSize", "1").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contributionId").value(older.getId().toString()))
                .andExpect(jsonPath("$.content[0].referenceCode").value(
                        receiptRepository.findByContributionId(older.getId()).orElseThrow().getReferenceCode()))
                .andExpect(jsonPath("$.content[0].receiptStatus").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.content[0].receiptStatusLabel").value("Exitoso"))
                .andExpect(jsonPath("$.content[0].contributionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("SUCCEEDED"));

        mockMvc.perform(get("/api/contributions/me").cookie(otherSponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].contributionId").value(privateContribution.getId().toString()));
        mockMvc.perform(get("/api/contributions/me").cookie(creatorSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPONSOR_ROLE_REQUIRED"));
        mockMvc.perform(get("/api/contributions/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyHistoryHasRequiredMessageAndInvalidFiltersAreRejected() throws Exception {
        Cookie sponsorSession = register("history-empty@test.local", "sponsor");

        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.emptyMessage").value("Aún no has realizado aportes"));
        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession).param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession).param("status", "CANCELLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void pendingAndFailedContributionsDoNotReceiveReceiptsAndRefundWebhooksDoNotRegress() throws Exception {
        Cookie sponsorSession = register("history-transitions@test.local", "sponsor");
        User sponsor = findUser("history-transitions@test.local");
        User creator = userRepository.save(new User(
                "History Creator", "history-transitions-creator@test.local", "hashed",
                UserRole.CREATOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        Campaign campaign = campaign(creator, "Transiciones");

        MvcResult successfulResult = mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                        .cookie(sponsorSession)
                        .header("Idempotency-Key", "history-success-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.05,\"currency\":\"USD\",\"paymentMethodId\":\"sim_success\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10.05))
                .andReturn();
        String successBody = successfulResult.getResponse().getContentAsString();
        UUID contributionId = UUID.fromString(extractValue(successBody, "id", 0));
        UUID paymentId = UUID.fromString(extractValue(successBody, "id", successBody.indexOf("\"payment\"")));
        TransactionReceipt receipt = receiptRepository.findByContributionId(contributionId).orElseThrow();
        assertEquals(TransactionReceiptStatus.SUCCESSFUL, receipt.getStatus());
        assertNotNull(receipt.getIssuedAt());
        assertTrue(receipt.getReferenceCode().startsWith("REF-"));

        String payload = "{\"event\":\"refund\"}";
        String signature = contributionService.sign(
                payload, "history-refund-event", paymentId, "payment.refunded", "refunded");
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Payment-Signature", signature)
                        .header("X-Provider-Event-Id", "tampered-refund-event")
                        .param("paymentId", paymentId.toString())
                        .param("eventType", "payment.refunded")
                        .param("status", "refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Payment-Signature", signature)
                        .header("X-Provider-Event-Id", "history-refund-event")
                        .param("paymentId", paymentId.toString())
                        .param("eventType", "payment.refunded")
                        .param("status", "refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Payment-Signature", signature)
                        .header("X-Provider-Event-Id", "history-refund-event")
                        .param("paymentId", paymentId.toString())
                        .param("eventType", "payment.refunded")
                        .param("status", "refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        String laterSuccessPayload = "{\"event\":\"late-success\"}";
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Payment-Signature", contributionService.sign(
                                laterSuccessPayload, "history-late-success-event", paymentId,
                                "payment.succeeded", "succeeded"))
                        .header("X-Provider-Event-Id", "history-late-success-event")
                        .param("paymentId", paymentId.toString())
                        .param("eventType", "payment.succeeded")
                        .param("status", "succeeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(laterSuccessPayload))
                .andExpect(status().isOk());

        assertEquals(TransactionReceiptStatus.REFUNDED,
                receiptRepository.findByContributionId(contributionId).orElseThrow().getStatus());
        assertNotNull(receiptRepository.findByContributionId(contributionId).orElseThrow().getRefundedAt());
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from payment_events where provider_event_id in (?, ?)",
                Integer.class,
                "history-refund-event",
                "history-late-success-event"));
        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession).param("status", "REFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contributionStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.content[0].receiptStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.content[0].receiptStatusLabel").value("Reembolsado"));

        MvcResult failedResult = mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                        .cookie(sponsorSession)
                        .header("Idempotency-Key", "history-failed-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3.15,\"currency\":\"USD\",\"paymentMethodId\":\"sim_failure\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andReturn();
        UUID failedId = UUID.fromString(extractValue(failedResult.getResponse().getContentAsString(), "id", 0));
        assertNull(receiptRepository.findByContributionId(failedId).orElse(null));
        mockMvc.perform(get("/api/contributions/me").cookie(sponsorSession).param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contributionId").value(failedId.toString()))
                .andExpect(jsonPath("$.content[0].contributionStatus").value("FAILED"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.content[0].referenceCode").doesNotExist());
    }

    private Cookie register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"History User\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Campaign campaign(User creator, String title) {
        return campaignRepository.saveAndFlush(new Campaign(
                creator.getId(), title, "Descripción de prueba", new BigDecimal("1000.00"),
                Instant.now().plusSeconds(86400), "Comunidad", null, "USD", CampaignStatus.ACTIVE));
    }

    private Contribution contribution(Campaign campaign, User sponsor, String amount,
                                      ContributionStatus contributionStatus, PaymentStatus paymentStatus,
                                      TransactionReceiptStatus receiptStatus) {
        Contribution contribution = contributionRepository.saveAndFlush(new Contribution(
                campaign, sponsor.getId(), new BigDecimal(amount), "USD", contributionStatus));
        if (paymentStatus != null) {
            Payment payment = new Payment(contribution, "test-provider", contribution.getAmount(), BigDecimal.ZERO);
            payment.setStatus(paymentStatus);
            paymentRepository.saveAndFlush(payment);
        }
        if (receiptStatus != null) {
            receiptRepository.saveAndFlush(new TransactionReceipt(
                    contribution,
                    "REF-" + UUID.randomUUID(),
                    receiptStatus));
        }
        return contribution;
    }

    private String extractValue(String json, String field, int searchFrom) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker, searchFrom);
        if (start < 0) throw new IllegalArgumentException("Field not found in response");
        start += marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
