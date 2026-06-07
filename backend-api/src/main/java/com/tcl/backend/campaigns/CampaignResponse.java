package com.tcl.backend.campaigns;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Response DTO returned by campaign APIs.
// It exposes campaign data as JSON without exposing the full JPA entity graph.
public record CampaignResponse(
        UUID id,
        String title,
        String description,
        String coverImageUrl,
        BigDecimal targetAmount,
        BigDecimal currentRaised,
        CampaignStatus status,
        String onChainAddress,
        UUID createdBy,
        UUID approvedBy,
        Instant createdAt
) {

    // Convert database entity Campaign into API response DTO.
    static CampaignResponse from(Campaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getCoverImageUrl(),
                campaign.getTargetAmount(),
                campaign.getCurrentRaised(),
                campaign.getStatus(),
                campaign.getOnChainAddress(),
                // Return only creator id, not the full AppUser object.
                campaign.getCreatedBy().getAccountId(),
                // approvedBy can be null while campaign is pending/rejected before approval.
                campaign.getApprovedBy() == null ? null : campaign.getApprovedBy().getAccountId(),
                campaign.getCreatedAt()
        );
    }
}
