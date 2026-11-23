package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignDiscussionComment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CampaignDiscussionCommentRepository extends JpaRepository<CampaignDiscussionComment, UUID> {
    Page<CampaignDiscussionComment> findByCampaignId(UUID campaignId, Pageable pageable);
    Page<CampaignDiscussionComment> findByCampaignIdAndFaqTrueAndOfficialAnswerTrue(UUID campaignId, Pageable pageable);
    boolean existsByParentCommentIdAndOfficialAnswerTrue(UUID parentCommentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CampaignDiscussionComment c where c.id = :id")
    Optional<CampaignDiscussionComment> findByIdForUpdate(@Param("id") UUID id);
}
