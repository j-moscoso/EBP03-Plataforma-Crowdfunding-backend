package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.NotificationRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignMediaType;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.FundingModel;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.ContributionService;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContributionNotificationIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ContributionService contributionService;

    @Test
    void successfulFixedContributionNotifiesCreatorOnceAndUpdatesUnreadCount() throws Exception {
        Cookie creatorSession = register("Campaign creator", "notify-creator@test.local", "creator");
        Cookie sponsorSession = register("Ana P\\u00e9rez", "notify-sponsor@test.local", "sponsor");
        User creator = userRepository.findByEmail("notify-creator@test.local").orElseThrow();
        User sponsor = userRepository.findByEmail("notify-sponsor@test.local").orElseThrow();
        Campaign campaign = campaign(creator, FundingModel.FIXED);

        MvcResult contribution = createContribution(
                sponsorSession, campaign, "notify-fixed", "25", "sim_success", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn();
        UUID contributionId = extractUuid(contribution.getResponse().getContentAsString(), "\"id\":\"");
        String notificationPage = mockMvc.perform(get("/api/notifications/me")
                        .cookie(creatorSession)
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("NEW_CONTRIBUTION"))
                .andExpect(jsonPath("$.content[0].campaignId").value(campaign.getId().toString()))
                .andExpect(jsonPath("$.content[0].contributionId").value(contributionId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Nuevo aporte recibido"))
                .andExpect(jsonPath("$.content[0].body")
                        .value("¡Nuevo aporte! Ana P\u00e9rez ha aportado $25.00 USD"))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID notificationId = extractUuid(notificationPage, "\"id\":\"");

        org.assertj.core.api.Assertions.assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNull(
                creator.getId())).isEqualTo(1);
        mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                        .cookie(sponsorSession)
                        .header("Idempotency-Key", "notify-fixed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contributionRequest("25", "sim_success", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(contributionId.toString()));
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/notifications/me").cookie(sponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .cookie(sponsorSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .cookie(creatorSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .cookie(creatorSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/me")
                        .cookie(creatorSession)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));

        UUID paymentId = extractUuid(contribution.getResponse().getContentAsString(), "\"payment\":{\"id\":\"");
        String payload = "{\"event\":\"payment.succeeded\"}";
        String eventType = "payment.succeeded";
        String signature = contributionService.sign(payload, "notify-webhook-event", paymentId, eventType, "succeeded");
        mockMvc.perform(post("/api/payments/webhook")
                        .header("X-Payment-Signature", signature)
                        .header("X-Provider-Event-Id", "notify-webhook-event")
                        .param("paymentId", paymentId.toString())
                        .param("eventType", eventType)
                        .param("status", "succeeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNull(
                creator.getId())).isZero();
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anonymousAndFailedContributionsUseTheExpectedNotificationPolicy() throws Exception {
        Cookie creatorSession = register("Creator", "notify-anonymous-creator@test.local", "creator");
        Cookie sponsorSession = register("Sponsor", "notify-anonymous-sponsor@test.local", "sponsor");
        User creator = userRepository.findByEmail("notify-anonymous-creator@test.local").orElseThrow();
        Campaign campaign = campaign(creator, FundingModel.FIXED);

        createContribution(sponsorSession, campaign, "notify-failure", "10", "sim_failure", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));

        createContribution(sponsorSession, campaign, "notify-anonymous", "10", "sim_success", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"));
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].body")
                        .value("¡Nuevo aporte! Anónimo ha aportado $10.00 USD"))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void flexibleContributionNotifiesWithEscapedSponsorNameAndValidatedCurrency() throws Exception {
        Cookie creatorSession = register("Flexible creator", "notify-flex-creator@test.local", "creator");
        Cookie sponsorSession = register("Sponsor", "notify-flex-sponsor@test.local", "sponsor");
        User creator = userRepository.findByEmail("notify-flex-creator@test.local").orElseThrow();
        User sponsor = userRepository.findByEmail("notify-flex-sponsor@test.local").orElseThrow();
        sponsor.updateName("<script>alert(1)</script>");
        userRepository.save(sponsor);
        Campaign campaign = campaign(creator, FundingModel.FLEXIBLE);

        createContribution(sponsorSession, campaign, "notify-flexible", "12.5", "sim_success", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/notifications/me").cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body")
                        .value("¡Nuevo aporte! &lt;script&gt;alert(1)&lt;/script&gt; ha aportado $12.50 USD"))
                .andExpect(jsonPath("$.content[0].body").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>"))))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    private Campaign campaign(User creator, FundingModel fundingModel) {
        return campaignRepository.save(new Campaign(
                creator.getId(),
                "Campaign notification",
                "Campaign for notification tests",
                new BigDecimal("1000.00"),
                Instant.now().plusSeconds(86_400),
                "Technology",
                null,
                "USD",
                CampaignMediaType.IMAGE,
                fundingModel,
                CampaignStatus.ACTIVE));
    }

    private org.springframework.test.web.servlet.ResultActions createContribution(
            Cookie sponsorSession,
            Campaign campaign,
            String idempotencyKey,
            String amount,
            String paymentMethodId,
            boolean anonymous) throws Exception {
        return mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                .cookie(sponsorSession)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contributionRequest(amount, paymentMethodId, anonymous)));
    }

    private String contributionRequest(String amount, String paymentMethodId, boolean anonymous) {
        return "{\"amount\":" + amount
                + ",\"currency\":\"USD\",\"paymentMethodId\":\"" + paymentMethodId
                + "\",\"anonymous\":" + anonymous + "}";
    }

    private Cookie register(String name, String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private UUID extractUuid(String json, String marker) {
        int markerIndex = json.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException("Expected UUID field not found");
        }
        int start = markerIndex + marker.length();
        return UUID.fromString(json.substring(start, start + 36));
    }
}
