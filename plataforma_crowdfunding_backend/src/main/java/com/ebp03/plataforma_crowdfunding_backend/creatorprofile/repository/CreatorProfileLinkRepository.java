package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.repository;

import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain.CreatorProfileLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorProfileLinkRepository extends JpaRepository<CreatorProfileLink, UUID> {
    List<CreatorProfileLink> findByProfile_IdOrderBySortOrderAscIdAsc(UUID profileId);

    @Modifying
    @Query("delete from CreatorProfileLink link where link.profile.id = :profileId")
    void deleteAllForProfile(@Param("profileId") UUID profileId);
}
