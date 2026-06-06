package com.tcl.backend.transactions;

import jakarta.validation.constraints.NotBlank;

public record ValidatorToggleRequest(
        @NotBlank String action
) {
}
