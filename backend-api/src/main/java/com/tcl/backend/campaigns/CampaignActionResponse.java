package com.tcl.backend.campaigns;

import java.util.UUID;

// Response DTO for campaign-changing actions such as create request, approve, or reject.
public record CampaignActionResponse(
        // Human-readable result message for frontend display.
        String message,
        // New/current campaign status after the action.
        CampaignStatus status,
        // Optional transaction id when the action published an on-chain transaction.
        // For example, admin approval creates a CREATE transaction. Reject does not need one.
        UUID transactionId
) {
}
