package com.ntropy.account.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.api.dto.CategoryExpenseAmount;
import com.ntropy.account.api.dto.MonthlyExpenseSummary;
import com.ntropy.account.mapper.MonthlyExpenseMapper;

import lombok.RequiredArgsConstructor;

/**
 * 사용자·연월별 소비 집계를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class MonthlyExpenseService {

    private final MonthlyExpenseMapper monthlyExpenseMapper;

    /**
     * 특정 사용자의 특정 연월 소비를 조회합니다.
     */
    @Transactional(readOnly = true)
    public MonthlyExpenseSummary findMonthlyExpense(
            Long userId,
            String yearMonth
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId는 양수여야 합니다."
            );
        }

        if (yearMonth == null || yearMonth.isBlank()) {
            throw new IllegalArgumentException(
                    "yearMonth는 필수입니다."
            );
        }

        /*
         * YYYY-MM 형식이 아니면 YearMonth.parse에서 예외가 발생합니다.
         */
        YearMonth targetMonth = YearMonth.parse(yearMonth);

        LocalDate startDate = targetMonth.atDay(1);
        LocalDate endDate = targetMonth
                .plusMonths(1)
                .atDay(1);

        Long totalExpense =
                monthlyExpenseMapper.findTotalExpense(
                        userId,
                        startDate,
                        endDate
                );

        Long fixedExpense =
                monthlyExpenseMapper.findFixedExpense(
                        userId,
                        startDate,
                        endDate
                );

        List<CategoryExpenseAmount> rows =
                monthlyExpenseMapper.findCategoryExpenses(
                        userId,
                        startDate,
                        endDate
                );

        Map<String, Long> categoryExpenses =
                new LinkedHashMap<>();

        if (rows != null) {
            for (CategoryExpenseAmount row : rows) {
                if (row == null) {
                    continue;
                }

                /*
                 * 소비 거래인데 category가 비어 있을 경우
                 * 임시로 ETC에 합산합니다.
                 *
                 * 실제 null 카테고리 정책은
                 * FastAPI/account-service 담당자와 합의 후 변경할 수 있습니다.
                 */
                String category = row.getCategory();

                if (category == null || category.isBlank()) {
                    category = "ETC";
                }

                long amount =
                        row.getExpenseAmount() == null
                                ? 0L
                                : row.getExpenseAmount();

                categoryExpenses.merge(
                        category,
                        amount,
                        Long::sum
                );
            }
        }

        return new MonthlyExpenseSummary(
                userId,
                yearMonth,
                totalExpense == null ? 0L : totalExpense,
                fixedExpense == null ? 0L : fixedExpense,
                categoryExpenses
        );
    }
}