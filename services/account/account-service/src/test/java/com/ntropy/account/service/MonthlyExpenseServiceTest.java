package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.CategoryExpenseAmount;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.account.mapper.MonthlyExpenseMapper;

class MonthlyExpenseServiceTest {

    @Test
    void aggregatesTotalFixedAndCategoryExpenses() {
        InMemoryMonthlyExpenseMapper mapper = new InMemoryMonthlyExpenseMapper();
        mapper.totalExpense = 900_000L;
        mapper.fixedExpense = 300_000L;
        mapper.categoryExpenses = List.of(
                new CategoryExpenseAmount("FOOD", 600_000L),
                new CategoryExpenseAmount("TRANSPORTATION", 300_000L)
        );
        MonthlyExpenseService service = new MonthlyExpenseService(mapper);

        MonthlyExpenseSummary summary = service.findMonthlyExpense(1L, "2026-07");

        assertEquals(900_000L, summary.getTotalExpense());
        assertEquals(300_000L, summary.getFixedExpense());
        assertEquals(Map.of("FOOD", 600_000L, "TRANSPORTATION", 300_000L), summary.getCategoryExpenses());
    }

    @Test
    void defaultsNullTotalAndFixedExpenseToZero() {
        InMemoryMonthlyExpenseMapper mapper = new InMemoryMonthlyExpenseMapper();
        mapper.totalExpense = null;
        mapper.fixedExpense = null;
        MonthlyExpenseService service = new MonthlyExpenseService(mapper);

        MonthlyExpenseSummary summary = service.findMonthlyExpense(1L, "2026-07");

        assertEquals(0L, summary.getTotalExpense());
        assertEquals(0L, summary.getFixedExpense());
    }

    @Test
    void mergesBlankCategoryIntoEtc() {
        InMemoryMonthlyExpenseMapper mapper = new InMemoryMonthlyExpenseMapper();
        mapper.totalExpense = 100_000L;
        mapper.fixedExpense = 0L;
        mapper.categoryExpenses = List.of(
                new CategoryExpenseAmount(null, 40_000L),
                new CategoryExpenseAmount("", 10_000L),
                new CategoryExpenseAmount("ETC", 50_000L)
        );
        MonthlyExpenseService service = new MonthlyExpenseService(mapper);

        MonthlyExpenseSummary summary = service.findMonthlyExpense(1L, "2026-07");

        assertEquals(Map.of("ETC", 100_000L), summary.getCategoryExpenses());
    }

    @Test
    void rejectsNullOrNonPositiveUserId() {
        InMemoryMonthlyExpenseMapper mapper = new InMemoryMonthlyExpenseMapper();
        MonthlyExpenseService service = new MonthlyExpenseService(mapper);

        assertThrows(IllegalArgumentException.class, () -> service.findMonthlyExpense(null, "2026-07"));
        assertThrows(IllegalArgumentException.class, () -> service.findMonthlyExpense(0L, "2026-07"));
    }

    @Test
    void rejectsBlankYearMonth() {
        InMemoryMonthlyExpenseMapper mapper = new InMemoryMonthlyExpenseMapper();
        MonthlyExpenseService service = new MonthlyExpenseService(mapper);

        assertThrows(IllegalArgumentException.class, () -> service.findMonthlyExpense(1L, null));
    }

    /**
     * 형식이 어긋난 yearMonth는 명시적으로 잡아서 감싸지 않고 YearMonth.parse()가
     * 던지는 DateTimeParseException을 그대로 전파한다(기존 동작, 이번 이슈 범위 밖).
     */
    @Test
    void malformedYearMonthPropagatesDateTimeParseException() {
        InMemoryMonthlyExpenseMapper mapper = new InMemoryMonthlyExpenseMapper();
        MonthlyExpenseService service = new MonthlyExpenseService(mapper);

        assertThrows(DateTimeParseException.class, () -> service.findMonthlyExpense(1L, "202607"));
    }

    private static class InMemoryMonthlyExpenseMapper implements MonthlyExpenseMapper {

        private Long totalExpense;
        private Long fixedExpense;
        private List<CategoryExpenseAmount> categoryExpenses = List.of();

        @Override
        public Long findTotalExpense(
                Long userId, LocalDate startDate, LocalDate endDate) {
            return totalExpense;
        }

        @Override
        public List<CategoryExpenseAmount> findCategoryExpenses(
                Long userId, LocalDate startDate, LocalDate endDate) {
            return categoryExpenses;
        }

        @Override
        public Long findFixedExpense(
                Long userId, LocalDate startDate, LocalDate endDate) {
            return fixedExpense;
        }
    }
}
