package com.ntropy.account.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.api.dto.CategoryExpenseAmount;

/**
 * 월별 소비 집계를 담당하는 MyBatis Mapper입니다.
 */
@Mapper
public interface MonthlyExpenseMapper {

    /**
     * 특정 기간의 총소비 금액을 조회합니다.
     */
    Long findTotalExpense(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 기간의 카테고리별 소비 금액을 조회합니다.
     */
    List<CategoryExpenseAmount> findCategoryExpenses(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 특정 기간의 고정지출 합계를 조회합니다.
     */
    Long findFixedExpense(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}