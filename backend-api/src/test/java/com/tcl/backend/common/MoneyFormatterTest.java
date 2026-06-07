package com.tcl.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyFormatterTest {

    private final MoneyFormatter formatter = new MoneyFormatter();

    @Test
    void canonicalKeepsTwoDecimalPlaces() {
        assertThat(formatter.canonical(new BigDecimal("50"))).isEqualTo("50.00");
        assertThat(formatter.canonical(new BigDecimal("50.1"))).isEqualTo("50.10");
    }
}
