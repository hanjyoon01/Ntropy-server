package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.mapper.IncomingTransactionQueryMapper;
import com.ntropy.account.mapper.projection.IncomingTransactionRow;
import com.ntropy.account.api.dto.internal.NormalizedIncomingTransaction;

class LocalIncomingTransactionQueryClientTest {

    @Test
    void returnsNormalizedStructureWithBankSpecificCounterpartyPrefixRemoved() {
        StubIncomingTransactionQueryMapper mapper = new StubIncomingTransactionQueryMapper(List.of(
                row(101L, PersonalBank.IBK_INDUSTRIAL_BANK, LocalDate.of(2026, 8, 1),
                        LocalTime.of(9, 10), "우아한형제들", "무시", new BigDecimal("350000")),
                row(102L, PersonalBank.JEONBUK_BANK, LocalDate.of(2026, 8, 2),
                        null, "무시", "홈)쿠팡 이츠", new BigDecimal("180000"))
        ));
        LocalIncomingTransactionQueryClient client = new LocalIncomingTransactionQueryClient(mapper);

        List<NormalizedIncomingTransaction> result = client.findIncomingTransactions(
                7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)
        );

        assertEquals(7L, mapper.userId);
        assertEquals(LocalDate.of(2026, 8, 1), mapper.startDate);
        assertEquals(LocalDate.of(2026, 8, 31), mapper.endDate);
        assertEquals(new NormalizedIncomingTransaction(
                101L, LocalDate.of(2026, 8, 1), LocalTime.of(9, 10),
                "우아한형제들", new BigDecimal("350000")
        ), result.get(0));
        assertEquals(new NormalizedIncomingTransaction(
                102L, LocalDate.of(2026, 8, 2), null,
                "쿠팡 이츠", new BigDecimal("180000")
        ), result.get(1));
    }

    @Test
    void rejectsReversedDateRangeBeforeQueryingMapper() {
        StubIncomingTransactionQueryMapper mapper = new StubIncomingTransactionQueryMapper(List.of());
        LocalIncomingTransactionQueryClient client = new LocalIncomingTransactionQueryClient(mapper);

        assertThrows(IllegalArgumentException.class, () -> client.findIncomingTransactions(
                7L, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)
        ));
        assertEquals(0, mapper.calls);
    }

    private static IncomingTransactionRow row(
            Long transactionId,
            PersonalBank bank,
            LocalDate date,
            LocalTime time,
            String desc1,
            String desc3,
            BigDecimal amount
    ) {
        IncomingTransactionRow row = new IncomingTransactionRow();
        row.setTransactionId(transactionId);
        row.setOrganizationCode(bank.getOrganizationCode());
        row.setTransactionDate(date);
        row.setTransactionTime(time);
        row.setAmount(amount);
        row.setDesc1(desc1);
        row.setDesc3(desc3);
        return row;
    }

    private static class StubIncomingTransactionQueryMapper implements IncomingTransactionQueryMapper {

        private final List<IncomingTransactionRow> rows;
        private int calls;
        private Long userId;
        private LocalDate startDate;
        private LocalDate endDate;

        private StubIncomingTransactionQueryMapper(List<IncomingTransactionRow> rows) {
            this.rows = rows;
        }

        @Override
        public List<IncomingTransactionRow> findByUserIdAndDateRange(
                Long userId,
                LocalDate startDate,
                LocalDate endDate
        ) {
            calls++;
            this.userId = userId;
            this.startDate = startDate;
            this.endDate = endDate;
            return rows;
        }
    }
}
