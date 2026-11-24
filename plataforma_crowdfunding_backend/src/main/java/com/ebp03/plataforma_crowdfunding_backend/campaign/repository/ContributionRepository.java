package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {
    List<Contribution> findByCampaignIdAndStatus(UUID campaignId, ContributionStatus status);
    boolean existsByCampaignIdAndStatus(UUID campaignId, ContributionStatus status);
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.campaign.id = :campaignId and c.status in :statuses")
    BigDecimal sumAmountByCampaignIdAndStatuses(@Param("campaignId") UUID campaignId, @Param("statuses") List<ContributionStatus> statuses);
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.campaign.id = :campaignId and c.status in :statuses and upper(c.currency) = upper(:currency)")
    BigDecimal sumAmountByCampaignIdStatusesAndCurrency(@Param("campaignId") UUID campaignId,
                                                        @Param("statuses") List<ContributionStatus> statuses,
                                                        @Param("currency") String currency);
    java.util.Optional<Contribution> findBySponsorIdAndIdempotencyKey(UUID sponsorId, String idempotencyKey);
    @EntityGraph(attributePaths = "campaign")
    org.springframework.data.domain.Page<Contribution> findBySponsorId(UUID sponsorId, org.springframework.data.domain.Pageable pageable);
    @EntityGraph(attributePaths = "campaign")
    org.springframework.data.domain.Page<Contribution> findBySponsorIdAndStatus(UUID sponsorId, ContributionStatus status, org.springframework.data.domain.Pageable pageable);
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.campaign.id = :campaignId and c.status = :status")
    BigDecimal sumAmountByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") ContributionStatus status);

    @Query(value = """
            WITH campaign_contributions AS (
                SELECT campaign_id, sponsor_id, amount, UPPER(currency) AS currency,
                       COALESCE(confirmed_at, created_at) AS contributed_at
                FROM contributions
                WHERE campaign_id IS NOT NULL AND status = 'CONFIRMED'
                UNION ALL
                SELECT campaign_id, sponsor_id, amount, UPPER(currency) AS currency,
                       created_at AS contributed_at
                FROM subscription_campaign_allocations
            ),
            single_currency AS (
                SELECT campaign_id, MIN(currency) AS currency
                FROM campaign_contributions
                GROUP BY campaign_id
                HAVING COUNT(DISTINCT currency) = 1
            ),
            personal_totals AS (
                SELECT campaign_id, currency, SUM(amount) AS amount_contributed,
                       MAX(contributed_at) AS last_contribution_at
                FROM campaign_contributions
                WHERE sponsor_id = :sponsorId
                GROUP BY campaign_id, currency
            ),
            campaign_totals AS (
                SELECT campaign_id, currency, SUM(amount) AS raised_amount
                FROM campaign_contributions
                GROUP BY campaign_id, currency
            )
            SELECT CAST(campaign.id AS VARCHAR(36)) AS "campaignId",
                   campaign.media_url AS "imageUrl",
                   campaign.media_type AS "mediaType",
                   campaign.title AS "title", personal.amount_contributed AS "amountContributedByMe",
                   personal.currency AS "currency", raised.raised_amount AS "raisedAmount",
                   campaign.goal_amount AS "goalAmount",
                   personal.last_contribution_at AS "lastContributionAt",
                   campaign.status AS "statusCode"
            FROM campaigns campaign
            JOIN single_currency allowed ON allowed.campaign_id = campaign.id
            JOIN personal_totals personal ON personal.campaign_id = campaign.id
                AND personal.currency = allowed.currency
            JOIN campaign_totals raised ON raised.campaign_id = campaign.id
                AND raised.currency = allowed.currency
            ORDER BY personal.last_contribution_at DESC, campaign.id DESC
            """,
            countQuery = """
                    WITH campaign_contributions AS (
                        SELECT campaign_id, sponsor_id, amount, UPPER(currency) AS currency
                        FROM contributions
                        WHERE campaign_id IS NOT NULL AND status = 'CONFIRMED'
                        UNION ALL
                        SELECT campaign_id, sponsor_id, amount, UPPER(currency) AS currency
                        FROM subscription_campaign_allocations
                    ),
                    single_currency AS (
                        SELECT campaign_id, MIN(currency) AS currency
                        FROM campaign_contributions
                        GROUP BY campaign_id
                        HAVING COUNT(DISTINCT currency) = 1
                    ),
                    personal_totals AS (
                        SELECT campaign_id, currency
                        FROM campaign_contributions
                        WHERE sponsor_id = :sponsorId
                        GROUP BY campaign_id, currency
                    )
                    SELECT COUNT(DISTINCT personal.campaign_id)
                    FROM personal_totals personal
                    JOIN single_currency allowed ON allowed.campaign_id = personal.campaign_id
                        AND allowed.currency = personal.currency
                    """,
            nativeQuery = true)
    Page<SupportedCampaignProjection> findSupportedCampaigns(
            @Param("sponsorId") UUID sponsorId,
            Pageable pageable);

    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT all_contributions.campaign_id
                FROM (
                    SELECT campaign_id, sponsor_id, currency
                    FROM contributions
                    WHERE campaign_id IS NOT NULL AND status = 'CONFIRMED'
                    UNION ALL
                    SELECT campaign_id, sponsor_id, currency
                    FROM subscription_campaign_allocations
                ) all_contributions
                GROUP BY all_contributions.campaign_id
                HAVING COUNT(DISTINCT UPPER(all_contributions.currency)) > 1
                    AND EXISTS (
                        SELECT 1
                        FROM (
                            SELECT campaign_id, sponsor_id, status
                            FROM contributions
                            WHERE campaign_id IS NOT NULL AND status = 'CONFIRMED'
                            UNION ALL
                            SELECT campaign_id, sponsor_id, 'CONFIRMED' AS status
                            FROM subscription_campaign_allocations
                        ) personal
                        WHERE personal.campaign_id = all_contributions.campaign_id
                            AND personal.sponsor_id = :sponsorId
                            AND personal.status = 'CONFIRMED'
                    )
            ) mixed
            """, nativeQuery = true)
    long countMixedCurrencyCampaignsForSponsor(@Param("sponsorId") UUID sponsorId);

    @Query(value = """
            SELECT CAST(campaign.id AS VARCHAR(36)) AS "campaignId",
                   campaign.title AS "title",
                     UPPER(contribution.currency) AS "currency"
            FROM campaigns campaign
              JOIN (
                  SELECT campaign_id, sponsor_id, currency
                  FROM contributions
                  WHERE campaign_id IS NOT NULL AND status = 'CONFIRMED'
                  UNION ALL
                  SELECT campaign_id, sponsor_id, currency
                  FROM subscription_campaign_allocations
              ) contribution ON contribution.campaign_id = campaign.id
              AND EXISTS (
                  SELECT 1
                  FROM (
                      SELECT campaign_id, sponsor_id, status
                      FROM contributions
                      WHERE campaign_id IS NOT NULL AND status = 'CONFIRMED'
                      UNION ALL
                      SELECT campaign_id, sponsor_id, 'CONFIRMED' AS status
                      FROM subscription_campaign_allocations
                  ) personal
                  WHERE personal.campaign_id = campaign.id
                    AND personal.sponsor_id = :sponsorId
                    AND personal.status = 'CONFIRMED'
              )
            GROUP BY campaign.id, campaign.title, UPPER(contribution.currency)
            ORDER BY campaign.id, UPPER(contribution.currency)
            """, nativeQuery = true)
    List<SupportedCampaignCurrencyProjection> findSupportedCampaignCurrencies(
            @Param("sponsorId") UUID sponsorId);

    @Query(value = """
            SELECT CAST(ranking.sponsor_id AS VARCHAR(36)) AS "sponsorId",
                   sponsor.name AS "sponsorName",
                   SUM(ranking.amount) AS "totalAmount"
            FROM (
                SELECT sponsor_id, amount
                FROM contributions
                WHERE campaign_id = :campaignId
                    AND status IN ('CONFIRMED', 'HELD')
                    AND is_anonymous = FALSE
                UNION ALL
                SELECT sponsor_id, amount
                FROM subscription_campaign_allocations
                WHERE campaign_id = :campaignId
            ) ranking
            JOIN users sponsor ON sponsor.id = ranking.sponsor_id
            GROUP BY ranking.sponsor_id, sponsor.name
            ORDER BY SUM(ranking.amount) DESC, sponsor.name ASC, ranking.sponsor_id ASC
            LIMIT 5
            """, nativeQuery = true)
    List<CampaignSponsorRankingProjection> findTopSponsorsByCampaignId(
            @Param("campaignId") UUID campaignId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from Contribution c
            where c.campaign.id = :campaignId and upper(c.currency) <> upper(:currency)
            """)
    boolean existsWithDifferentCurrency(
            @Param("campaignId") UUID campaignId,
            @Param("currency") String currency);

    @Query("select count(distinct c.sponsorId) from Contribution c where c.campaign.id = :campaignId and c.status = :status")
    Long countDistinctSponsorByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") ContributionStatus status);
    @Query("select count(distinct c.sponsorId) from Contribution c where c.campaign.id = :campaignId and c.status in :statuses")
    Long countDistinctSponsorByCampaignIdAndStatuses(@Param("campaignId") UUID campaignId, @Param("statuses") List<ContributionStatus> statuses);
    @Query("select distinct c.sponsorId from Contribution c where c.campaign.id = :campaignId and c.status in :statuses")
    List<UUID> findDistinctSponsorIdsByCampaignIdAndStatuses(
            @Param("campaignId") UUID campaignId,
            @Param("statuses") List<ContributionStatus> statuses);
    @Query("select distinct c.sponsorId from Contribution c where c.campaign.id = :campaignId and c.status = :status and c.sponsorId <> :creatorId")
    List<UUID> findDistinctSponsorIdsByCampaignIdAndStatusExcludingCreator(
            @Param("campaignId") UUID campaignId,
            @Param("status") ContributionStatus status,
            @Param("creatorId") UUID creatorId);
}
