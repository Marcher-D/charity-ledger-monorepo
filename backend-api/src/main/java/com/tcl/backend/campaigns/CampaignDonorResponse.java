package com.tcl.backend.campaigns;

import com.tcl.backend.transactions.DonorSummary;
import com.tcl.backend.transactions.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

// Response DTO for GET /api/v1/campaigns/{id}/donors.
// It shows donation/transaction summary rows for one campaign.
public record CampaignDonorResponse(
        String username,
        BigDecimal amount,
        String txHash,
        TransactionStatus status,
        Instant createdAt
) {

    // Convert DonorSummary projection into API response DTO.
    // DonorSummary comes from a custom repository query in TransactionCacheRepository.
    static CampaignDonorResponse from(DonorSummary donor) {
        return new CampaignDonorResponse(
                donor.getUsername(),
                donor.getAmount(),
                donor.getTxHash(),
                donor.getStatus(),
                donor.getCreatedAt()
        );
    }
}
