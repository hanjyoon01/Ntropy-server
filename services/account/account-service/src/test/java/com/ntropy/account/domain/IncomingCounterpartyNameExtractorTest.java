package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IncomingCounterpartyNameExtractorTest {

    @Test
    void extractsIbkCounterpartyFromDesc1() {
        String result = IncomingCounterpartyNameExtractor.extract(
                PersonalBank.IBK_INDUSTRIAL_BANK.getOrganizationCode(),
                "쿠팡풀필먼트서비스", "펌이체", "다른 적요", "다른 지점"
        );

        assertEquals("쿠팡풀필먼트서비스", result);
    }

    @Test
    void extractsOtherSupportedBanksCounterpartyFromDesc3() {
        for (PersonalBank bank : PersonalBank.values()) {
            if (bank == PersonalBank.IBK_INDUSTRIAL_BANK) {
                continue;
            }

            String result = IncomingCounterpartyNameExtractor.extract(
                    bank.getOrganizationCode(), "다른 예금주", "다른 거래구분", "쿠팡이츠", "다른 지점"
            );

            assertEquals("쿠팡이츠", result, bank.getDisplayName());
        }
    }

    @Test
    void removesJeonbukPrefixAndPreservesComparisonCharacters() {
        String result = IncomingCounterpartyNameExtractor.extract(
                PersonalBank.JEONBUK_BANK.getOrganizationCode(),
                null, null, "홈) Ｃｏｕｐａｎｇ-Ｅａｔｓ ", null
        );

        assertEquals("Ｃｏｕｐａｎｇ-Ｅａｔｓ ", result);
    }

    @Test
    void preservesJeonbukTextWhenPrefixIsNotAtTheBeginning() {
        String result = IncomingCounterpartyNameExtractor.extract(
                PersonalBank.JEONBUK_BANK.getOrganizationCode(),
                null, null, "쿠팡홈)서비스", null
        );

        assertEquals("쿠팡홈)서비스", result);
    }

    @Test
    void preservesSamePrefixForOtherBanks() {
        String result = IncomingCounterpartyNameExtractor.extract(
                PersonalBank.SHINHAN_BANK.getOrganizationCode(),
                null, null, "홈)쿠팡이츠", null
        );

        assertEquals("홈)쿠팡이츠", result);
    }

    @Test
    void returnsNullWhenJeonbukCounterpartyContainsOnlyPrefixAndSpaces() {
        String result = IncomingCounterpartyNameExtractor.extract(
                PersonalBank.JEONBUK_BANK.getOrganizationCode(),
                null, null, "홈)   ", null
        );

        assertNull(result);
    }

    @Test
    void returnsNullWhenBankSpecificCounterpartyFieldIsBlank() {
        assertNull(IncomingCounterpartyNameExtractor.extract(
                PersonalBank.SHINHAN_BANK.getOrganizationCode(),
                "desc1 값", "desc2 값", "   ", "desc4 값"
        ));
    }
}
