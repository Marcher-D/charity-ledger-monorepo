package com.tcl.backend.transactions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Repository for reading/writing TransactionCache rows in transactions_cache.
// JpaRepository gives common methods like save, saveAndFlush, findById, and delete.
public interface TransactionCacheRepository extends JpaRepository<TransactionCache, UUID> {

    // Spring Data derived query:
    // Find transactions where transaction.user.accountId equals accountId.
    // Sort is passed separately so callers can request newest-first order.
    List<TransactionCache> findByUserAccountId(UUID accountId, Sort sort);

    // Find one transaction by txHash. Optional because a hash may not exist.
    Optional<TransactionCache> findByTxHash(String txHash);

    // Custom JPQL query for GET /api/v1/campaigns/{id}/donors.
    // It selects only the fields needed by DonorSummary instead of full TransactionCache entities.
    @Query("""
            select t.user.account.username as username,
                   t.amount as amount,
                   t.txHash as txHash,
                   t.status as status,
                   t.createdAt as createdAt
            from TransactionCache t
            where t.campaign.id = :campaignId
              and t.type = com.tcl.backend.transactions.TransactionType.DONATE
            order by t.createdAt desc
            """)
    // Spring fills DonorSummary projection getters using the aliases above:
    // username, amount, txHash, status, createdAt.
    List<DonorSummary> findDonorsByCampaignId(@Param("campaignId") UUID campaignId);
}
