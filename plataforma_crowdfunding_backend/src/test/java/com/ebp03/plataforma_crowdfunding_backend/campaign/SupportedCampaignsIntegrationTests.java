package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignMediaType;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
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
class SupportedCampaignsIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ContributionRepository contributionRepository;

    @Test
    void sponsorSeesConfirmedAggregatesOnlyAndDatabaseOrderedPages() throws Exception {
        Cookie sponsorSession = register("supported-owner@test.local", "sponsor");
        Cookie otherSponsorSession = register("supported-other@test.local", "sponsor");
        register("supported-creator@test.local", "creator");
        User sponsor = findUser("supported-owner@test.local");
        User otherSponsor = findUser("supported-other@test.local");
        User creator = findUser("supported-creator@test.local");

        Campaign older = campaign(creator, "Bosques de la vida", "100.00", CampaignStatus.ACTIVE,
                "https://cdn.example/forest.mp4");
        older.setMediaType(CampaignMediaType.VIDEO);
        campaignRepository.saveAndFlush(older);
        Instant firstConfirmed = Instant.parse("2026-10-15T03:10:00Z");
        Instant latestConfirmed = Instant.parse("2026-10-16T03:10:00Z");
        contribution(older, sponsor, "20.11", "USD", ContributionStatus.CONFIRMED, firstConfirmed);
        contribution(older, sponsor, "4.90", "USD", ContributionStatus.CONFIRMED, latestConfirmed);
        contribution(older, sponsor, "900.00", "USD", ContributionStatus.PENDING, latestConfirmed.plusSeconds(30));
        contribution(older, sponsor, "800.00", "USD", ContributionStatus.FAILED, latestConfirmed.plusSeconds(40));
        contribution(older, sponsor, "700.00", "USD", ContributionStatus.REFUNDED, latestConfirmed.plusSeconds(50));
        contribution(older, otherSponsor, "70.00", "USD", ContributionStatus.CONFIRMED, latestConfirmed.minusSeconds(10));
        contribution(older, otherSponsor, "600.00", "USD", ContributionStatus.PENDING, latestConfirmed.plusSeconds(60));

        Campaign newer = campaign(creator, "Restauración costera", "10.00", CampaignStatus.PUBLISHED, null);
        Instant newerConfirmed = Instant.parse("2026-10-17T03:10:00Z");
        contribution(newer, sponsor, "11.00", "USD", ContributionStatus.CONFIRMED, newerConfirmed);
        Campaign notSupported = campaign(creator, "Otra persona", "50.00", CampaignStatus.ACTIVE, null);
        contribution(notSupported, otherSponsor, "25.00", "USD", ContributionStatus.CONFIRMED, newerConfirmed);

        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("page", "0")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].campaignId").value(newer.getId().toString()))
                .andExpect(jsonPath("$.content[0].amountContributedByMe").value(11.00))
                .andExpect(jsonPath("$.content[0].raisedAmount").value(11.00))
                .andExpect(jsonPath("$.content[0].goalAmount").value(10.00))
                .andExpect(jsonPath("$.content[0].totalRaisedPercentage").value(100.00))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.content[0].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVA"))
                .andExpect(jsonPath("$.content[0].statusCode").value("PUBLISHED"))
                .andExpect(jsonPath("$.content[0].lastContributionAt").value(newerConfirmed.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("page", "1")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].campaignId").value(older.getId().toString()))
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://cdn.example/forest.mp4"))
                .andExpect(jsonPath("$.content[0].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.content[0].amountContributedByMe").value(25.01))
                .andExpect(jsonPath("$.content[0].raisedAmount").value(95.01))
                .andExpect(jsonPath("$.content[0].totalRaisedPercentage").value(95.01))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVA"))
                .andExpect(jsonPath("$.content[0].statusCode").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].lastContributionAt").value(latestConfirmed.toString()));

        MvcResult otherSponsorResult = mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(otherSponsorSession)
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        org.hamcrest.MatcherAssert.assertThat(otherSponsorResult.getResponse().getContentAsString(),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(newer.getId().toString())));
    }

    @Test
    void requiresSponsorRejectsClientScopeAndValidatesPagination() throws Exception {
        Cookie sponsorSession = register("supported-access@test.local", "sponsor");
        Cookie creatorSession = register("supported-creator-access@test.local", "creator");

        mockMvc.perform(get("/api/sponsors/me/supported-campaigns").cookie(sponsorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.emptyMessage").value("Aún no has apoyado ningún proyecto"));
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns").cookie(creatorSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPONSOR_ROLE_REQUIRED"));
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("sponsorId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("campaignIds", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("pageSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void statusStrategyMapsAllCampaignStatesAndOmitsLegacyMixedCurrencyCampaigns() throws Exception {
        Cookie sponsorSession = register("supported-status@test.local", "sponsor");
        register("supported-status-other@test.local", "sponsor");
        register("supported-creator-status@test.local", "creator");
        User sponsor = findUser("supported-status@test.local");
        User otherSponsor = findUser("supported-status-other@test.local");
        User creator = findUser("supported-creator-status@test.local");
        CampaignStatus[] states = CampaignStatus.values();
        String[] labels = {"BORRADOR", "ACTIVA", "ACTIVA", "EXITOSA", "FALLIDA", "CANCELADA"};
        for (int index = 0; index < states.length; index++) {
            Campaign campaign = campaign(creator, "Estado " + states[index], "3.00", states[index], null);
            contribution(campaign, sponsor, "2.00", "USD", ContributionStatus.CONFIRMED,
                    Instant.parse("2026-10-16T00:00:00Z").plusSeconds(index));
        }

        Campaign mixed = campaign(creator, "Monedas mezcladas", "100.00", CampaignStatus.ACTIVE, null);
        contribution(mixed, sponsor, "2.00", "USD", ContributionStatus.CONFIRMED, Instant.now());
        contribution(mixed, otherSponsor, "3.00", "EUR", ContributionStatus.CONFIRMED, Instant.now());

        MvcResult result = mockMvc.perform(get("/api/sponsors/me/supported-campaigns")
                        .cookie(sponsorSession)
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(states.length))
                .andExpect(jsonPath("$.omittedCampaigns[0].campaignId").value(mixed.getId().toString()))
                .andExpect(jsonPath("$.omittedCampaigns[0].reasonCode").value("MIXED_CONFIRMED_CURRENCIES"))
                .andExpect(jsonPath("$.omittedCampaigns[0].currencies[0]").value("EUR"))
                .andExpect(jsonPath("$.omittedCampaigns[0].currencies[1]").value("USD"))
                .andExpect(jsonPath("$.warnings[0].code").value("SUPPORTED_CAMPAIGNS_OMITTED"))
                .andExpect(jsonPath("$.warnings[0].affectedCampaignCount").value(1))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        org.hamcrest.MatcherAssert.assertThat(response,
                org.hamcrest.Matchers.containsString("\"totalRaisedPercentage\":66.67"));
        for (int index = 0; index < states.length; index++) {
            org.hamcrest.MatcherAssert.assertThat(response,
                    org.hamcrest.Matchers.containsString("\"status\":\"" + labels[index] + "\""));
            org.hamcrest.MatcherAssert.assertThat(response,
                    org.hamcrest.Matchers.containsString("\"statusCode\":\"" + states[index].name() + "\""));
        }
        org.hamcrest.MatcherAssert.assertThat(response,
                org.hamcrest.Matchers.containsString(mixed.getId().toString()));
    }

    @Test
    void contributionWithDifferentCurrencyIsRejected() throws Exception {
        Cookie sponsorSession = register("supported-currency@test.local", "sponsor");
        register("supported-currency-creator@test.local", "creator");
        User sponsor = findUser("supported-currency@test.local");
        User creator = findUser("supported-currency-creator@test.local");
        Campaign campaign = campaign(creator, "Moneda única", "100.00", CampaignStatus.ACTIVE, null);
        contribution(campaign, sponsor, "10.00", "USD", ContributionStatus.CONFIRMED, Instant.now());

        mockMvc.perform(post("/api/campaigns/{campaignId}/contributions", campaign.getId())
                        .cookie(sponsorSession)
                        .header("Idempotency-Key", "different-currency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5.00,\"currency\":\"EUR\",\"paymentMethodId\":\"sim_success\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_CURRENCY_MISMATCH"));
    }

    private Cookie register(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Supported Campaign User\",\"email\":\"" + email
                                + "\",\"password\":\"Secure1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("AUTH_SESSION");
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Campaign campaign(User creator, String title, String goal, CampaignStatus state, String imageUrl) {
        return campaignRepository.saveAndFlush(new Campaign(
                creator.getId(), title, "Descripción de prueba", new BigDecimal(goal),
                Instant.now().plusSeconds(86400), "Comunidad", imageUrl, "USD", state));
    }

    private void contribution(Campaign campaign, User sponsor, String amount, String currency,
                              ContributionStatus state, Instant confirmedAt) {
        Contribution contribution = new Contribution(
                campaign, sponsor.getId(), new BigDecimal(amount), currency, state);
        if (state == ContributionStatus.CONFIRMED) {
            contribution.setConfirmedAt(confirmedAt);
        }
        contributionRepository.saveAndFlush(contribution);
    }
}
