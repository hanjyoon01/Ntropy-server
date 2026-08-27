package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccountNoMaskTest {

    @Test
    void exposesOnlyLastFourCharacters() {
        assertEquals("****6789", AccountNoMask.mask("110123456789"));
        assertEquals("****6789", AccountNoMask.mask("110-123-456789"));
    }

    @Test
    void fullyMasksShortValues() {
        assertEquals("****", AccountNoMask.mask("1234"));
    }

    @Test
    void rejectsMissingAccountNumber() {
        assertThrows(IllegalArgumentException.class, () -> AccountNoMask.mask(null));
        assertThrows(IllegalArgumentException.class, () -> AccountNoMask.mask(" "));
    }
}
