package com.ntropy.account.api.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * CODEF·NTROPY 미제공 필드는 임의 기본값 없이 명시적 null로 내려가야 하고,
 * 내부 식별 필드(accountNoHash, codefConnectionId, fingerprint)는 외부 응답에 노출되면 안 된다.
 */
class AccountSummaryJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializesUnsetLoanAndSavingsFieldsAsExplicitNull() throws Exception {
        AccountSummary summary = new AccountSummary(
                1L, "VIRTUAL", "0088", "신한은행", "DEPOSIT_TRUST", "11",
                "****1234", "신한은행 가상 수시입출금", BigDecimal.ZERO,
                null, null, "KRW", LocalDate.of(2025, 1, 1), null, LocalDate.of(2026, 6, 30),
                false, null, "ACTIVE"
        );

        String json = objectMapper.writeValueAsString(summary);

        assertTrue(json.contains("\"loanContractPrincipal\":null"));
        assertTrue(json.contains("\"interestRate\":null"));
        assertTrue(json.contains("\"maturityDate\":null"));
        assertTrue(json.contains("\"nextPaymentDate\":null"));
    }

    @Test
    void serializesUnsetLoanFieldsOnTransactionAsExplicitNull() throws Exception {
        AccountTransactionSummary summary = new AccountTransactionSummary(
                1001L, 101L, "ORDINARY", LocalDate.of(2026, 6, 30), LocalTime.of(23, 50),
                BigDecimal.ZERO, BigDecimal.valueOf(1500), null, null, null,
                BigDecimal.valueOf(3245000), null, "이자", "예금이자", null
        );

        String json = objectMapper.writeValueAsString(summary);

        assertTrue(json.contains("\"loanTransactionTypeName\":null"));
        assertTrue(json.contains("\"loanPrincipalAmount\":null"));
        assertTrue(json.contains("\"loanInterestAmount\":null"));
    }

    @Test
    void neverExposesInternalIdentifierFields() {
        assertFalse(hasComponent(AccountSummary.class, "accountNoHash"));
        assertFalse(hasComponent(AccountSummary.class, "codefConnectionId"));
        assertFalse(hasComponent(AccountTransactionSummary.class, "fingerprint"));
    }

    private static boolean hasComponent(Class<? extends Record> recordType, String name) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
