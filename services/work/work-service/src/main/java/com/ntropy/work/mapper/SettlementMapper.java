package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.enums.SettlementMatchStatus;

@Mapper
public interface SettlementMapper {

    void insert(Settlement settlement);

    /** 이 거래(accountTransactionId)가 이미 MATCHED로 처리됐는지 - 배치 재실행 중복 방지용. */
    boolean existsByAccountTransactionId(@Param("accountTransactionId") Long accountTransactionId);

    boolean existsByUserIdAndStatusAndPeriod(@Param("userId") Long userId,
                                              @Param("status") SettlementMatchStatus status,
                                              @Param("periodStart") LocalDate periodStart,
                                              @Param("periodEnd") LocalDate periodEnd);

    List<Settlement> findByUserIdAndDepositDateRange(@Param("userId") Long userId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);

    /** AI 리포트 배치용 벌크 조회: 여러 사용자의 SETTLEMENT를 한 번에 조회한다. 결과는 userId로 그룹핑해서 써야 한다. */
    List<Settlement> findByUserIdInAndDepositDateRange(@Param("userIds") List<Long> userIds,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    /** PER_TASK 잡의 최근 N개월 평균 소득 계산용: 여러 잡의 SETTLEMENT를 한 번에 조회한다. 결과는 jobId로 그룹핑해서 써야 한다. */
    List<Settlement> findByJobIdInAndDepositDateRangeAndStatus(@Param("jobIds") List<Long> jobIds,
                                                                 @Param("startDate") LocalDate startDate,
                                                                 @Param("endDate") LocalDate endDate,
                                                                 @Param("status") SettlementMatchStatus status);
}
