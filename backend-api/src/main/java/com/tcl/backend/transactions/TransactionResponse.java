package com.tcl.backend.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Response DTO for transaction history/listing endpoints.
// Example use: GET /api/v1/transactions/me.
// This is the fuller transaction record shape.
public record TransactionResponse(
        UUID id,
        String txHash,
        TransactionType type,
        UUID campaignId,
        BigDecimal amount,
        String note,
        TransactionStatus status,
        Instant createdAt
) {

    // Convert TransactionCache entity into API response DTO.
    // txHash may be null if publishing/signing has not completed.
    static TransactionResponse from(TransactionCache transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTxHash(),
                transaction.getType(),
                transaction.getCampaign() == null ? null : transaction.getCampaign().getId(),
                transaction.getAmount(),
                transaction.getNote(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
