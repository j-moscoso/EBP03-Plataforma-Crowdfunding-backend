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
    java.util.Optional<Contribution> findBySponsorIdAndIdempotencyKey(UUID sponsorId, String idempotencyKey);
    @EntityGraph(attributePaths = "campaign")
    org.springframework.data.domain.Page<Contribution> findBySponsorId(UUID sponsorId, org.springframework.data.domain.Pageable pageable);
    @EntityGraph(attributePaths = "campaign")
    org.springframework.data.domain.Page<Contribution> findBySponsorIdAndStatus(UUID sponsorId, ContributionStatus status, org.springframework.data.domain.Pageable pageable);
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.campaign.id = :campaignId and c.status = :status")
    BigDecimal sumAmountByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") ContributionStatus status);

    @Query(value = """
            WITH single_currency AS (
                SELECT campaign_id, MIN(UPPER(currency)) AS currency
                FROM contributions
                WHERE status = 'CONFIRMED'
                GROUP BY campaign_id
                HAVING COUNT(DISTINCT UPPER(currency)) = 1
            ),
            personal_totals AS (
                SELECT campaign_id, UPPER(currency) AS currency, SUM(amount) AS amount_contributed,
                       MAX(COALESCE(confirmed_at, created_at)) AS last_contribution_at
                FROM contributions
                WHERE sponsor_id = :sponsorId AND status = 'CONFIRMED'
                GROUP BY campaign_id, UPPER(currency)
            ),
            campaign_totals AS (
                SELECT campaign_id, UPPER(currency) AS currency, SUM(amount) AS raised_amount
                FROM contributions
                WHERE status = 'CONFIRMED'
                GROUP BY campaign_id, UPPER(currency)
            )
            SELECT CAST(campaign.id AS VARCHAR(36)) AS "campaignId",
                   campaign.media_url AS "imageUrl",
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
                    WITH single_currency AS (
                        SELECT campaign_id, MIN(UPPER(currency)) AS currency
                        FROM contributions
                        WHERE status = 'CONFIRMED'
                        GROUP BY campaign_id
                        HAVING COUNT(DISTINCT UPPER(currency)) = 1
                    ),
                    personal_totals AS (
                        SELECT campaign_id, UPPER(currency) AS currency
                        FROM contributions
                        WHERE sponsor_id = :sponsorId AND status = 'CONFIRMED'
                        GROUP BY campaign_id, UPPER(currency)
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
                FROM contributions all_contributions
                WHERE all_contributions.status = 'CONFIRMED'
                GROUP BY all_contributions.campaign_id
                HAVING COUNT(DISTINCT UPPER(all_contributions.currency)) > 1
                    AND EXISTS (
                        SELECT 1
                        FROM contributions personal
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
            JOIN contributions contribution ON contribution.campaign_id = campaign.id
            WHERE contribution.status = 'CONFIRMED'
              AND EXISTS (
                  SELECT 1
                  FROM contributions personal
                  WHERE personal.campaign_id = campaign.id
                    AND personal.sponsor_id = :sponsorId
                    AND personal.status = 'CONFIRMED'
              )
            GROUP BY campaign.id, campaign.title, UPPER(contribution.currency)
            ORDER BY campaign.id, UPPER(contribution.currency)
            """, nativeQuery = true)
    List<SupportedCampaignCurrencyProjection> findSupportedCampaignCurrencies(
            @Param("sponsorId") UUID sponsorId);

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
    @Query("select distinct c.sponsorId from Contribution c where c.campaign.id = :campaignId and c.status = :status and c.sponsorId <> :creatorId")
    List<UUID> findDistinctSponsorIdsByCampaignIdAndStatusExcludingCreator(
            @Param("campaignId") UUID campaignId,
            @Param("status") ContributionStatus status,
            @Param("creatorId") UUID creatorId);
}
