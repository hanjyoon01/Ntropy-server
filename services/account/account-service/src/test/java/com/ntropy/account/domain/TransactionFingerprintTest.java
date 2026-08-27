package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TransactionFingerprintTest {

    @Test
    void normalizesEquivalentAmounts() {
        assertEquals(
                TransactionFingerprint.hash(1L, new BigDecimal("1000.00")),
                TransactionFingerprint.hash(1L, new BigDecimal("1000"))
        );
    }

    @Test
    void changesWhenAnyComponentChanges() {
        assertNotEquals(
                TransactionFingerprint.hash(1L, "20260101", "1000"),
                TransactionFingerprint.hash(1L, "20260101", "2000")
        );
    }
}
