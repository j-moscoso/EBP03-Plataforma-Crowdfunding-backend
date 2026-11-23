package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignDiscussionCommentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
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
class CampaignDiscussionIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignDiscussionCommentRepository commentRepository;

    @Test
    void sponsorPublishesQuestionCreatorRepliesAndFaqIsConsistent() throws Exception {
        Cookie creatorSession = register("discussion-owner@test.local", "creator");
        Cookie sponsorSession = register("discussion-sponsor@test.local", "sponsor");
        User creator = findUser("discussion-owner@test.local");
        User sponsor = findUser("discussion-sponsor@test.local");
        Campaign campaign = campaign(creator, CampaignStatus.ACTIVE);

        MvcResult questionResult = mockMvc.perform(post("/api/campaigns/{campaignId}/discussion/comments", campaign.getId())
                        .cookie(sponsorSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"¿Cuál será la fecha de entrega?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(sponsor.getId().toString()))
                .andExpect(jsonPath("$.campaignId").value(campaign.getId().toString()))
                .andExpect(jsonPath("$.parentCommentId").doesNotExist())
                .andReturn();
        java.util.UUID questionId = java.util.UUID.fromString(
                questionResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));

        MvcResult replyResult = mockMvc.perform(post("/api/campaigns/{campaignId}/discussion/comments/{commentId}/replies",
                        campaign.getId(), questionId)
                        .cookie(creatorSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"La entrega está prevista para marzo.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCommentId").value(questionId.toString()))
                .andReturn();
        java.util.UUID replyId = java.util.UUID.fromString(
                replyResult.getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1"));

        mockMvc.perform(patch("/api/campaigns/{campaignId}/discussion/comments/{commentId}/faq",
                        campaign.getId(), replyId).cookie(creatorSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isFaq\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOfficialAnswer").value(true))
                .andExpect(jsonPath("$.isFaq").value(true));

        mockMvc.perform(patch("/api/campaigns/{campaignId}/discussion/comments/{commentId}/faq",
                        campaign.getId(), replyId).cookie(creatorSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/campaigns/{campaignId}/faq", campaign.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].isOfficialAnswer").value(true))
                .andExpect(jsonPath("$.content[0].question.body").value("¿Cuál será la fecha de entrega?"));

        var answer = commentRepository.findById(replyId).orElseThrow();
        assertTrue(answer.isFaq());
        assertTrue(answer.isOfficialAnswer());
        assertEquals(questionId, answer.getParentCommentId());
    }

    @Test
    void invalidAndUnauthorizedDiscussionOperationsAreRejected() throws Exception {
        Cookie creatorSession = register("discussion-owner-2@test.local", "creator");
        Cookie sponsorSession = register("discussion-sponsor-2@test.local", "sponsor");
        User creator = findUser("discussion-owner-2@test.local");
        Campaign campaign = campaign(creator, CampaignStatus.ACTIVE);

        mockMvc.perform(post("/api/campaigns/{campaignId}/discussion/comments", campaign.getId())
                        .cookie(sponsorSession).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("body"));
        mockMvc.perform(post("/api/campaigns/{campaignId}/discussion/comments", campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Pregunta\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/campaigns/{campaignId}/discussion/comments", campaign.getId())
                        .cookie(sponsorSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].code").value("INVALID_HTML"));
        mockMvc.perform(get("/api/campaigns/{campaignId}/discussion", campaign.getId())
                        .param("page", "-1").param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Cookie register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Campaign campaign(User creator, CampaignStatus status) {
        return campaignRepository.saveAndFlush(new Campaign(
                creator.getId(), "Discusión", "Descripción", new BigDecimal("1000.00"),
                Instant.now().plusSeconds(86400), "Comunidad", null, status));
    }
}
