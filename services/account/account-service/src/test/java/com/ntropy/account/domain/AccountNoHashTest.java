package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccountNoHashTest {

    @Test
    void producesSameHashForSameOrganizationAndAccountNo() {
        String first = AccountNoHash.hash("0004", "110-123-456789");
        String second = AccountNoHash.hash("0004", "110-123-456789");

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void producesDifferentHashForDifferentOrganization() {
        String kb = AccountNoHash.hash("0004", "110-123-456789");
        String shinhan = AccountNoHash.hash("0088", "110-123-456789");

        assertNotEquals(kb, shinhan);
    }

    @Test
    void rejectsBlankInputs() {
        assertThrows(IllegalArgumentException.class, () -> AccountNoHash.hash(null, "110-123-456789"));
        assertThrows(IllegalArgumentException.class, () -> AccountNoHash.hash("0004", ""));
    }
}
