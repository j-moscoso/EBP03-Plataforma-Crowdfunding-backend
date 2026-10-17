package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionReceiptRepository extends JpaRepository<TransactionReceipt, UUID> {
    Optional<TransactionReceipt> findByContributionId(UUID contributionId);
    List<TransactionReceipt> findByContributionIdIn(List<UUID> contributionIds);
}
