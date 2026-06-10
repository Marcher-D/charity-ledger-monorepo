package com.tcl.backend.campaigns;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateCampaignRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        @Size(max = 500) String coverImageUrl,
        @NotNull @DecimalMin(value = "0.01") BigDecimal targetAmount
) {
}
