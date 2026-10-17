package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignUpdateRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampaignUpdatePublicationIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private ContributionRepository contributionRepository;
    @Autowired
    private CampaignUpdateRepository updateRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void creatorPublishesUpdateWithOrderedMediaAndOnlyConfirmedSponsorsAreNotifiedOnce() throws Exception {
        Cookie creatorSession = register("update-owner@test.local", "creator");
        Cookie sponsorOneSession = register("update-sponsor-one@test.local", "sponsor");
        Cookie sponsorTwoSession = register("update-sponsor-two@test.local", "sponsor");
        Cookie outsiderSession = register("update-outsider@test.local", "sponsor");
        User creator = findUser("update-owner@test.local");
        User sponsorOne = findUser("update-sponsor-one@test.local");
        User sponsorTwo = findUser("update-sponsor-two@test.local");
        Campaign campaign = campaign(creator, CampaignStatus.ACTIVE);
        contribute(campaign, sponsorOne, ContributionStatus.CONFIRMED);
        contribute(campaign, sponsorOne, ContributionStatus.CONFIRMED);
        contribute(campaign, sponsorOne, ContributionStatus.PENDING);
        contribute(campaign, sponsorOne, ContributionStatus.FAILED);
        contribute(campaign, sponsorOne, ContributionStatus.REFUNDED);
        contribute(campaign, sponsorTwo, ContributionStatus.CONFIRMED);

        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                        .cookie(creatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(
                                "Avance del proyecto",
                                "Ya completamos la primera etapa.\nSin contenido HTML.",
                                "[{\"mediaType\":\"VIDEO\",\"url\":\"https://cdn.example/video.mp4\",\"sortOrder\":1},"
                                        + "{\"mediaType\":\"IMAGE\",\"url\":\"https://cdn.example/avance.jpg\",\"sortOrder\":0}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignId").value(campaign.getId().toString()))
                .andExpect(jsonPath("$.authorId").value(creator.getId().toString()))
                .andExpect(jsonPath("$.title").value("Avance del proyecto"))
                .andExpect(jsonPath("$.notificationsCreated").value(2))
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.media[0].sortOrder").value(0))
                .andExpect(jsonPath("$.media[1].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.media[1].sortOrder").value(1));

        UUID updateId = updateRepository.findByCampaignIdOrderByPublishedAtDesc(campaign.getId()).getFirst().getId();
        mockMvc.perform(get("/api/notifications/me").cookie(sponsorOneSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CAMPAIGN_UPDATE_PUBLISHED"))
                .andExpect(jsonPath("$.content[0].campaignUpdateId").value(updateId.toString()));
        mockMvc.perform(get("/api/notifications/me").cookie(sponsorTwoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/notifications/me").cookie(outsiderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void publicationWithoutSponsorsSucceedsWithoutNotifications() throws Exception {
        Cookie creatorSession = register("update-no-sponsors@test.local", "creator");
        Campaign campaign = campaign(findUser("update-no-sponsors@test.local"), CampaignStatus.ACTIVE);

        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                        .cookie(creatorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Avance", "Actualización sin patrocinadores.", "[]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationsCreated").value(0));
    }

    @Test
    void campaignWallIsScopedPaginatedAndOrdersNewestFirst() throws Exception {
        Cookie creatorSession = register("update-wall@test.local", "creator");
        User creator = findUser("update-wall@test.local");
        Campaign selected = campaign(creator, CampaignStatus.ACTIVE);
        Campaign other = campaign(creator, CampaignStatus.PUBLISHED);

        postUpdate(creatorSession, selected.getId(), validBody("Primero", "Primer avance.", "[]"))
                .andExpect(status().isCreated());
        postUpdate(creatorSession, selected.getId(), validBody("Segundo", "Segundo avance.", "[]"))
                .andExpect(status().isCreated());
        postUpdate(creatorSession, other.getId(), validBody("Otra campaña", "Otro muro.", "[]"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/campaigns/{campaignId}/updates", selected.getId())
                        .param("page", "0").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].campaignId").value(selected.getId().toString()))
                .andExpect(jsonPath("$.content[0].title").value("Segundo"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/campaigns/{campaignId}/updates", selected.getId())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/campaigns/{campaignId}/updates", selected.getId())
                        .param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("pageSize"));
    }

    @Test
    void rejectsAnonymousNonOwnerAndNonCreatorPublication() throws Exception {
        Cookie owner = register("update-auth-owner@test.local", "creator");
        Cookie otherCreator = register("update-auth-other@test.local", "creator");
        Cookie sponsor = register("update-auth-sponsor@test.local", "sponsor");
        Campaign campaign = campaign(findUser("update-auth-owner@test.local"), CampaignStatus.ACTIVE);
        Campaign otherCampaign = campaign(findUser("update-auth-other@test.local"), CampaignStatus.ACTIVE);

        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                        .cookie(sponsor).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                        .cookie(otherCreator).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", otherCampaign.getId())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isForbidden());
        assertEquals(0, updateRepository.countByCampaignId(campaign.getId()));
    }

    @Test
    void rejectsMissingClosedOrHiddenCampaigns() throws Exception {
        Cookie creatorSession = register("update-status@test.local", "creator");
        User creator = findUser("update-status@test.local");
        Campaign closed = campaign(creator, CampaignStatus.FAILED);
        Campaign draft = campaign(creator, CampaignStatus.DRAFT);

        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", closed.getId())
                        .cookie(creatorSession).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_CLOSED"));
        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", UUID.randomUUID())
                        .cookie(creatorSession).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/campaigns/{campaignId}/updates", draft.getId()))
                .andExpect(status().isNotFound());

        jdbcTemplate.update("update users set status = 'DISABLED' where id = ?", creator.getId());
        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign(creator, CampaignStatus.ACTIVE).getId())
                        .cookie(creatorSession).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Título", "Contenido válido", "[]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTIVE_CREATOR_REQUIRED"));
    }

    @Test
    void invalidTextMediaTypesUrlsAndUnknownFieldsAreNotPersisted() throws Exception {
        Cookie creatorSession = register("update-invalid@test.local", "creator");
        Campaign campaign = campaign(findUser("update-invalid@test.local"), CampaignStatus.ACTIVE);
        String[] invalidBodies = {
                validBody("   ", "Contenido.", "[]"),
                validBody(String.join("", java.util.Collections.nCopies(141, "T")), "Contenido.", "[]"),
                validBody("Título", " \n\t ", "[]"),
                validBody("Título", String.join("", java.util.Collections.nCopies(10001, "x")), "[]"),
                validBody("Título", "<script>alert(1)</script>", "[]"),
                validBody("Título", "javascript:alert(1)", "[]"),
                validBody("Título", "Contenido.", "[{\"mediaType\":\"IMAGE\",\"url\":\"http://cdn.example/image.jpg\"}]"),
                validBody("Título", "Contenido.", "[{\"mediaType\":\"IMAGE\",\"url\":\"javascript:alert(1)\"}]"),
                validBody("Título", "Contenido.", "[{\"mediaType\":\"AUDIO\",\"url\":\"https://cdn.example/audio.mp3\"}]"),
                validBody("Título", "Contenido.", "[" + String.join(",", java.util.Collections.nCopies(
                        11, "{\"mediaType\":\"IMAGE\",\"url\":\"https://cdn.example/image.jpg\"}")) + "]"),
                validBody("Título", "Contenido.", "[]")
                        .replace("\"media\":[]", "\"media\":[],\"authorId\":\"00000000-0000-0000-0000-000000000000\""),
                validBody("Título", "Contenido.", "[]")
                        .replace("\"media\":[]", "\"media\":[{\"mediaType\":\"IMAGE\",\"url\":\"https://cdn.example/image.jpg\",\"campaignId\":\"other\"}]")
        };
        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                            .cookie(creatorSession).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertEquals(0, updateRepository.countByCampaignId(campaign.getId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from campaign_update_media m join campaign_updates u on u.id = m.update_id where u.campaign_id = ?",
                Integer.class,
                campaign.getId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from notifications where campaign_id = ?", Integer.class, campaign.getId()));
    }

    @Test
    void persistenceFailureRollsBackUpdateMediaAndNotifications() throws Exception {
        Cookie creatorSession = register("update-rollback-owner@test.local", "creator");
        Cookie sponsorSession = register("update-rollback-sponsor@test.local", "sponsor");
        User creator = findUser("update-rollback-owner@test.local");
        Campaign campaign = campaign(creator, CampaignStatus.ACTIVE);
        contribute(campaign, findUser("update-rollback-sponsor@test.local"), ContributionStatus.CONFIRMED);
        jdbcTemplate.execute("alter table campaign_update_media add constraint ck_reject_update_media_url check (url <> 'https://fail.example')");

        mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaign.getId())
                        .cookie(creatorSession).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("Debe revertirse", "Contenido válido", "[{\"mediaType\":\"IMAGE\",\"url\":\"https://fail.example\"}]")))
                .andExpect(status().isConflict());

        assertEquals(0, updateRepository.countByCampaignId(campaign.getId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from campaign_update_media m join campaign_updates u on u.id = m.update_id where u.campaign_id = ?",
                Integer.class,
                campaign.getId()));
        mockMvc.perform(get("/api/notifications/me").cookie(sponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions postUpdate(Cookie session, UUID campaignId, String body)
            throws Exception {
        return mockMvc.perform(post("/api/campaigns/{campaignId}/updates", campaignId)
                .cookie(session).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Cookie register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Campaign campaign(User creator, CampaignStatus status) {
        return campaignRepository.saveAndFlush(new Campaign(
                creator.getId(),
                "Campaña de prueba",
                "Descripción de prueba",
                new BigDecimal("1000.00"),
                Instant.now().plusSeconds(86400),
                "Comunidad",
                null,
                status));
    }

    private void contribute(Campaign campaign, User sponsor, ContributionStatus status) {
        contributionRepository.saveAndFlush(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("25.00"), "USD", status));
    }

    private String validBody(String title, String body, String media) {
        return "{\"title\":\"" + title.replace("\"", "\\\"")
                + "\",\"body\":\"" + body.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                + "\",\"media\":" + media + "}";
    }
}
