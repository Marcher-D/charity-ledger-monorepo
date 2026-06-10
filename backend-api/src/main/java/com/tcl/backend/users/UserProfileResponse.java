package com.tcl.backend.users;

import java.math.BigDecimal;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        AccountRole role,
        String walletAddress,
        BigDecimal availableBalance,
        BigDecimal pendingBalance,
        BigDecimal stakedBalance,
        boolean activeValidator
) {
}
