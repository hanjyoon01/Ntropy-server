package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PersonalBankTest {

    @Test
    void exposesNineSupportedOrganizationCodes() {
        Set<String> organizationCodes = Stream.of(PersonalBank.values())
                .map(PersonalBank::getOrganizationCode)
                .collect(Collectors.toSet());

        assertEquals(9, organizationCodes.size());
        assertEquals(
                Set.of("0003", "0004", "0011", "0023", "0037", "0039", "0045", "0081", "0088"),
                organizationCodes
        );
        assertFalse(organizationCodes.contains("0031"));
    }

    @Test
    void identifiesBankSpecificLoginRules() {
        assertTrue(PersonalBank.IBK_INDUSTRIAL_BANK.isBirthDateRequired());
        assertTrue(PersonalBank.KB_KOOKMIN_BANK.isBirthDateRequired());
        assertFalse(PersonalBank.SHINHAN_BANK.isBirthDateRequired());
        assertEquals(3, PersonalBank.KYONGNAM_BANK.getPasswordErrorLimit().orElseThrow());
        assertTrue(PersonalBank.SC_BANK.getPasswordErrorLimit().isEmpty());
    }

    @Test
    void findsBankByOrganizationCode() {
        assertEquals(PersonalBank.SHINHAN_BANK, PersonalBank.fromOrganizationCode("0088"));
        assertThrows(IllegalArgumentException.class, () -> PersonalBank.fromOrganizationCode("0031"));
    }

    @Test
    void normalizesBirthDateOnlyForBanksThatRequireIt() {
        assertEquals("19900101", PersonalBank.KB_KOOKMIN_BANK.normalizeBirthDate("19900101"));
        assertEquals(null, PersonalBank.SHINHAN_BANK.normalizeBirthDate(null));
        assertEquals(null, PersonalBank.SHINHAN_BANK.normalizeBirthDate("19900101"));
    }

    @Test
    void rejectsMissingOrInvalidBirthDateForRequiredBanks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PersonalBank.IBK_INDUSTRIAL_BANK.normalizeBirthDate(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PersonalBank.KB_KOOKMIN_BANK.normalizeBirthDate("19900231")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PersonalBank.KB_KOOKMIN_BANK.normalizeBirthDate("not-a-date")
        );
    }
}
