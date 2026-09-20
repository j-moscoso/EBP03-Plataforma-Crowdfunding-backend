package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignReward;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampaignControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private ContributionRepository contributionRepository;

    @Test
    void creatorCanCreateAndUpdateDraftWithRewards() throws Exception {
        Cookie creator = registerCreator("creator.draft@test.local");

        MvcResult created = mockMvc.perform(post("/api/drafts")
                        .cookie(creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bosques del futuro\",\"description\":\"Proyecto de reforestación\",\"goalAmount\":25000,\"durationDays\":45,\"category\":\"Medio ambiente\",\"mediaUrl\":\"https://cdn.example.com/forest.jpg\",\"rewards\":[{\"title\":\"Agradecimiento\",\"description\":\"Mención virtual\",\"minimumAmount\":10},{\"title\":\"Kit de apoyo\",\"description\":\"Recompensa física\",\"minimumAmount\":50}] }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.rewards[0].title").value("Agradecimiento"))
                .andReturn();

        String draftId = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(put("/api/drafts/{draftId}", draftId)
                        .cookie(creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bosques del futuro v2\",\"goalAmount\":30000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Bosques del futuro v2"));
    }

    @Test
    void invalidDraftReturnsFieldErrorsAndDoesNotPersist() throws Exception {
        Cookie creator = registerCreator("creator.invalid@test.local");

        mockMvc.perform(post("/api/drafts")
                        .cookie(creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Proyecto incompleto\",\"description\":\"Sin meta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("goalAmount"))
                .andExpect(jsonPath("$.code").value("REQUIRED"));

        mockMvc.perform(get("/api/drafts")
                        .cookie(creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void otherUserCannotReadOrModifyAnotherCreatorsDraft() throws Exception {
        Cookie creatorA = registerCreator("creator.a@test.local");
        Cookie creatorB = registerCreator("creator.b@test.local");

        String createdDraft = mockMvc.perform(post("/api/drafts")
                        .cookie(creatorA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalAmount\":1200,\"durationDays\":30,\"category\":\"Medio ambiente\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String draftId = extractId(createdDraft);

        mockMvc.perform(get("/api/drafts/{draftId}", draftId)
                        .cookie(creatorB))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/drafts/{draftId}", draftId).cookie(creatorB))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishedDraftCreatesCampaignWithProgressAndRewards() throws Exception {
        Cookie creator = registerCreator("creator.publish@test.local");

        String draft = mockMvc.perform(post("/api/drafts")
                        .cookie(creator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Energía verde\",\"description\":\"Campaña de paneles solares\",\"goalAmount\":30000,\"durationDays\":60,\"category\":\"Medio ambiente\",\"mediaUrl\":\"https://example.com/solar.jpg\",\"rewards\":[{\"title\":\"Gracias\",\"minimumAmount\":10},{\"title\":\"Kit\",\"minimumAmount\":50},{\"title\":\"Membresía\",\"minimumAmount\":150}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String draftId = extractId(draft);

        MvcResult publishRes = mockMvc.perform(post("/api/drafts/{draftId}/publish", draftId)
                        .cookie(creator))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rewards.length()").value(3))
                .andExpect(jsonPath("$.progress.percentage").exists())
                .andReturn();

        String campaignId = extractId(publishRes.getResponse().getContentAsString());
        User sponsor = userRepository.save(new User("Sponsor de prueba", "sponsor.publish@test.local", "hashed", UserRole.SPONSOR, VerificationStatus.VERIFIED, com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus.ACTIVE));
        Campaign campaign = campaignRepository.findById(UUID.fromString(campaignId)).orElseThrow();
        contributionRepository.save(new Contribution(campaign, sponsor.getId(), new BigDecimal("13500.00"), "USD", ContributionStatus.CONFIRMED));
        contributionRepository.save(new Contribution(campaign, sponsor.getId(), new BigDecimal("600.00"), "USD", ContributionStatus.PENDING));
        contributionRepository.save(new Contribution(campaign, sponsor.getId(), new BigDecimal("1200.00"), "USD", ContributionStatus.FAILED));

        mockMvc.perform(get("/api/campaigns/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.raisedAmount").value(13500.0))
                .andExpect(jsonPath("$.progress.remainingAmount").value(16500.0))
                .andExpect(jsonPath("$.progress.sponsorsCount").value(1))
                .andExpect(jsonPath("$.progress.secondsRemaining").exists());
    }

        @Test
        void placeholderCampaignIdReturnsReadableValidationError() throws Exception {
                mockMvc.perform(get("/api/campaigns/{campaignId}", "{campaignId}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                                .andExpect(jsonPath("$.message").value("El parámetro campaignId debe ser un UUID válido."));
        }

        @Test
        void contributionIsIdempotentAndFailedPaymentCanBeRetried() throws Exception {
                User creator = userRepository.save(new User("Campaign creator", "creator.contribution@test.local", "hashed", UserRole.CREATOR, VerificationStatus.VERIFIED, com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus.ACTIVE));
                Campaign campaign = campaignRepository.save(new Campaign(creator.getId(), "Aporte test", "Campaña de prueba", new BigDecimal("1000.00"), Instant.now().plusSeconds(86400), "Tecnología", null, CampaignStatus.ACTIVE));
                CampaignReward reward = new CampaignReward(campaign, "Kit", "Kit de prueba", new BigDecimal("50.00"), 1, 0);
                campaign.setRewards(java.util.List.of(reward));
                campaignRepository.save(campaign);
                Cookie sponsor = registerSponsor("sponsor.contribution@test.local");

                MvcResult confirmed = mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                                .cookie(sponsor).header("Idempotency-Key", "contribution-key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":75,\"currency\":\"USD\",\"rewardId\":\"" + reward.getId() + "\",\"paymentMethodId\":\"sim_success\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("CONFIRMED"))
                        .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                        .andReturn();
                String contributionId = extractId(confirmed.getResponse().getContentAsString());

                mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                                .cookie(sponsor).header("Idempotency-Key", "contribution-key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":75,\"currency\":\"USD\",\"rewardId\":\"" + reward.getId() + "\",\"paymentMethodId\":\"sim_success\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(contributionId));

                MvcResult failed = mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                                .cookie(sponsor).header("Idempotency-Key", "contribution-key-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":25,\"currency\":\"USD\",\"paymentMethodId\":\"sim_failure\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("FAILED"))
                        .andReturn();
                String failedId = extractId(failed.getResponse().getContentAsString());
                mockMvc.perform(post("/api/contributions/{contributionId}/retry", failedId)
                                .cookie(sponsor).header("Idempotency-Key", "retry-key-1")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethodId\":\"sim_success\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CONFIRMED"));

                mockMvc.perform(get("/api/campaigns/{campaignId}/progress", campaign.getId()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.raisedAmount").value(100.0))
                        .andExpect(jsonPath("$.sponsorsCount").value(1));
        }

    private Cookie registerCreator(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Creator\",\"email\":\"" + email + "\",\"password\":\"Secure1!\",\"role\":\"creator\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        User creator = userRepository.findByEmail(email).orElseThrow();
        creator.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(creator);
        return result.getResponse().getCookie("AUTH_SESSION");
    }

        private Cookie registerSponsor(String email) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"name\":\"Sponsor\",\"email\":\"" + email + "\",\"password\":\"Secure1!\",\"role\":\"sponsor\"}"))
                                .andExpect(status().isCreated())
                                .andReturn();
                return result.getResponse().getCookie("AUTH_SESSION");
        }

    private String extractId(String json) {
        int index = json.indexOf("\"id\":\"");
        if (index < 0) {
            throw new IllegalArgumentException("No id found in: " + json);
        }
        int start = index + 6;
        return json.substring(start, start + 36);
    }
}
