package com.tcl.backend.transactions;

import java.math.BigDecimal;
import java.time.Instant;

// Spring Data projection for campaign donor summaries.
// It is not an entity and not a DTO record. It is an interface that Spring fills
// from selected columns in TransactionCacheRepository.findDonorsByCampaignId(...).
public interface DonorSummary {

    // Getter names must match aliases in the repository query, such as "as username".
    String getUsername();

    BigDecimal getAmount();

    String getTxHash();

    TransactionStatus getStatus();

    Instant getCreatedAt();
}
