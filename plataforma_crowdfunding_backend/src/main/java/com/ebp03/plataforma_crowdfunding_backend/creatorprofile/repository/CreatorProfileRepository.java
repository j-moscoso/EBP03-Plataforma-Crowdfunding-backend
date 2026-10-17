package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.repository;

import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain.CreatorProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, UUID> {
    Optional<CreatorProfile> findByUser_Id(UUID userId);
}
