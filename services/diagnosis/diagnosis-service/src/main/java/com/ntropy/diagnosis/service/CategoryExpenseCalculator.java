package com.ntropy.diagnosis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ntropy.diagnosis.api.dto.CategoryExpenseSummary;

/**
 * 소비 카테고리별 금액과 비율을 계산하는 Service입니다.
 *
 * 카테고리별 소비 금액은 account-service에서 전달받은
 * 카테고리별 집계 데이터를 사용합니다.
 */
@Service
public class CategoryExpenseCalculator {

    /**
     * 카테고리별 소비 금액과 비율을 계산합니다.
     *
     * @param categoryAmounts 카테고리별 소비 금액
     * @param totalExpense 전체 소비 금액
     * @return 카테고리별 소비 요약 목록
     */
    public List<CategoryExpenseSummary> calculate(
            Map<String, Long> categoryAmounts,
            Long totalExpense
    ) {
        // 카테고리 데이터가 없으면 빈 목록을 반환합니다.
        if (categoryAmounts == null
                || categoryAmounts.isEmpty()) {
            return List.of();
        }

        // 전체 소비 금액이 null이거나 0 이하이면
        // 비율을 계산할 수 없으므로 ratio를 null로 반환합니다.
        boolean canCalculateRatio =
                totalExpense != null && totalExpense > 0;

        return categoryAmounts.entrySet()
                .stream()
                // null 카테고리는 응답에서 제외합니다.
                .filter(entry -> entry.getKey() != null)
                // null 금액은 0원으로 처리합니다.
                .map(entry -> {
                    String category = entry.getKey();
                    Long amount = Objects.requireNonNullElse(
                            entry.getValue(),
                            0L
                    );

                    // 음수 소비 금액은 정상적인 소비 금액이 아니므로
                    // 0원으로 보정합니다.
                    long normalizedAmount =
                            Math.max(amount, 0L);

                    BigDecimal ratio = null;

                    if (canCalculateRatio) {
                        ratio = BigDecimal.valueOf(
                                        normalizedAmount
                                )
                                .divide(
                                        BigDecimal.valueOf(
                                                totalExpense
                                        ),
                                        4,
                                        RoundingMode.HALF_UP
                                );
                    }

                    return new CategoryExpenseSummary(
                            category,
                            normalizedAmount,
                            ratio
                    );
                })
                // 금액이 큰 카테고리부터 반환하면
                // 화면에서 주요 소비 카테고리를 먼저 표시할 수 있습니다.
                .sorted(
                        Comparator.comparing(
                                        CategoryExpenseSummary
                                                ::getAmount
                                )
                                .reversed()
                                .thenComparing(
                                        CategoryExpenseSummary
                                                ::getCategory
                                )
                )
                .toList();
    }
}