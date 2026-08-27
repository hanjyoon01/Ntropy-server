package com.ntropy.account.mapper;

import java.time.LocalDate;
import java.util.List;

import com.ntropy.account.api.dto.ClassificationTargetTransaction;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.mapper.projection.OwnedAccountTransactionRow;
import com.ntropy.account.mapper.projection.OwnedTransactionCountRow;

@Mapper
public interface FinancialDataQueryMapper {

    List<Account> findAccountsByUserId(@Param("userId") Long userId);

    Account findAccountByIdAndUserId(@Param("accountId") Long accountId,
                                     @Param("userId") Long userId);

    OwnedTransactionCountRow countTransactionsByAccountIdAndUserId(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<OwnedAccountTransactionRow> findTransactionsByAccountIdAndUserId(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<ClassificationTargetTransaction> findClassificationTargets(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth
    );

    /**
     * 주어진 거래 ID 중, 해당 사용자·연월 기준 분류 대상 조건을 실제로 만족하는 ID만 반환합니다.
     */
    List<Long> findValidTransactionIds(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("transactionIds") List<Long> transactionIds
    );

    /**
     * TXN_ANALYSIS가 아직 생성되지 않은 일간 소비 분석 대상 거래를
     * 조회합니다.
     */
    List<DailyClassificationTargetTransaction> findUnanalyzedTransactions(
            @Param("limit") int limit
    );

    List<DailyClassificationTargetTransaction> findUnanalyzedTransactionsByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );
}
