package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.mapper.projection.VirtualSettlementIncome;

@Mapper
public interface WorkLogPlatformIncomeMapper {

    void insert(WorkLogPlatformIncome income);

    void update(WorkLogPlatformIncome income);

    List<WorkLogPlatformIncome> findByLogId(@Param("logId") Long logId);

    void deleteByLogId(@Param("logId") Long logId);

    /**
     * 정산 배치용: 이 잡의 이 플랫폼으로 매핑된 income 중, 확정(CONFIRMED)된 근무일지의
     * 근무일이 기간 내에 있는 것만 조회한다 (WORK_LOG 조인).
     */
    List<WorkLogPlatformIncome> findConfirmedByJobIdAndPlatformIdAndDateRange(
            @Param("jobId") Long jobId,
            @Param("platformId") Long platformId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 소득분석용: 이 유저의 확정(CONFIRMED)된 근무일지 중 근무일이 기간 내에 있는 것의
     * income 행 전부를 조회한다 (WORK_LOG 조인). 아직 COMPLETED가 아닌 것만 필터링해서
     * "정산대기소득"을 계산하는 데 쓴다.
     */
    List<WorkLogPlatformIncome> findConfirmedByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * AI 리포트 배치용 벌크 조회: 여러 사용자의 확정(CONFIRMED) WORK_LOG_PLATFORM_INCOME을
     * 한 번에 조회한다. 이 엔티티에는 userId가 없으므로, 결과를 사용자별로 나누려면
     * 같은 기간에 벌크 조회한 WorkLog 목록(logId → userId)을 호출부에서 함께 이용해야 한다.
     */
    List<WorkLogPlatformIncome> findConfirmedByUserIdInAndDateRange(
            @Param("userIds") List<Long> userIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 가상 정산 입금용: 기준일까지 확정된 플랫폼 소득의 누적 목표액과 PENDING 여부를 조회한다. */
    List<VirtualSettlementIncome> findConfirmedByUserIdUpToDateForVirtualSettlement(
            @Param("userId") Long userId,
            @Param("endDate") LocalDate endDate);
}
