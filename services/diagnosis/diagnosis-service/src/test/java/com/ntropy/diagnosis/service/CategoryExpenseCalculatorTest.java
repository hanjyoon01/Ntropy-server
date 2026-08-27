package com.ntropy.diagnosis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.diagnosis.api.dto.CategoryExpenseSummary;

/**
 * 카테고리별 소비 금액·비율 계산을 검증하는 테스트입니다.
 */
class CategoryExpenseCalculatorTest {

    /**
     * 카테고리별 금액과 비율이 정상적으로 계산되는지 확인합니다.
     */
    @Test
    void calculate_returnsCategoryAmountAndRatio() {
        CategoryExpenseCalculator calculator =
                new CategoryExpenseCalculator();

        Map<String, Long> categoryAmounts =
                new LinkedHashMap<>();

        categoryAmounts.put("FOOD", 600_000L);
        categoryAmounts.put("TRANSPORTATION", 400_000L);

        List<CategoryExpenseSummary> result =
                calculator.calculate(
                        categoryAmounts,
                        1_000_000L
                );

        assertEquals(2, result.size());

        // 금액이 큰 FOOD가 먼저 반환되는지 확인합니다.
        assertEquals("FOOD", result.get(0).getCategory());
        assertEquals(600_000L, result.get(0).getAmount());
        assertEquals(
                BigDecimal.valueOf(0.6).setScale(4),
                result.get(0).getRatio()
        );

        assertEquals("TRANSPORTATION", result.get(1).getCategory());
        assertEquals(400_000L, result.get(1).getAmount());
        assertEquals(
                BigDecimal.valueOf(0.4).setScale(4),
                result.get(1).getRatio()
        );
    }

    /**
     * 전체 소비 금액이 0원이면 비율이 null인지 확인합니다.
     */
    @Test
    void calculate_whenTotalExpenseIsZero_returnsNullRatio() {
        CategoryExpenseCalculator calculator =
                new CategoryExpenseCalculator();

        Map<String, Long> categoryAmounts =
                Map.of(
                        "FOOD",
                        0L
                );

        List<CategoryExpenseSummary> result =
                calculator.calculate(
                        categoryAmounts,
                        0L
                );

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getAmount());
        assertNull(result.get(0).getRatio());
    }

    /**
     * 카테고리 데이터가 없으면 빈 목록을 반환하는지 확인합니다.
     */
    @Test
    void calculate_whenCategoryDataIsEmpty_returnsEmptyList() {
        CategoryExpenseCalculator calculator =
                new CategoryExpenseCalculator();

        List<CategoryExpenseSummary> result =
                calculator.calculate(
                        Map.of(),
                        0L
                );

        assertTrue(result.isEmpty());
    }

    /**
     * null 금액을 0원으로 처리하는지 확인합니다.
     */
    @Test
    void calculate_whenAmountIsNull_treatsAmountAsZero() {
        CategoryExpenseCalculator calculator =
                new CategoryExpenseCalculator();

        Map<String, Long> categoryAmounts =
                new LinkedHashMap<>();

        categoryAmounts.put("FOOD", null);

        List<CategoryExpenseSummary> result =
                calculator.calculate(
                        categoryAmounts,
                        1_000_000L
                );

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getAmount());
        assertEquals(
                BigDecimal.ZERO.setScale(4),
                result.get(0).getRatio()
        );
    }
}