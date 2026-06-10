package com.tcl.backend.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/*
 * Shared money formatting helper.
 *
 * Money must be represented consistently before saving or sending to the
 * blockchain side. For hashing/signing, "50", "50.0", and "50.00" are different
 * strings, even though humans see the same amount. This class forces a stable
 * two-decimal representation.
 */
@Component
public class MoneyFormatter {

    /*
     * Returns a BigDecimal with exactly two decimal places.
     *
     * Examples:
     * - 50    -> 50.00
     * - 50.1  -> 50.10
     * - 50.129 -> 50.13
     *
     * HALF_UP is the common "round .5 upward" rule used for user-facing money.
     */
    public BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            // Null is treated as zero so callers do not accidentally store null money values.
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /*
     * Returns the canonical string form used in outbound transaction messages.
     *
     * toPlainString avoids scientific notation, so values stay readable and stable.
     * Example: 50.00 instead of 5E+1.
     */
    public String canonical(BigDecimal amount) {
        return normalize(amount).toPlainString();
    }
}
