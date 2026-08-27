package com.ntropy.account.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.entity.DailyBatchExecution;

@Mapper
public interface DailyBatchExecutionMapper {

    /** 신규 실행 INSERT. UNIQUE(job_name, business_date) 위반 시 DuplicateKeyException이 발생한다. */
    void insert(@Param("execution") DailyBatchExecution execution,
                @Param("leaseDurationSeconds") long leaseDurationSeconds);

    /**
     * 만료됐거나(status != 'RUNNING' 또는 lease_until 경과) 완료된 기존 실행을 새 lease로 인계한다.
     * 영향받은 row 수가 1이어야 실제로 lease를 획득한 것이다.
     * "현재 시각"과 새 만료 시각을 UPDATE 문 안에서 DB {@code NOW()}로 함께 계산해, 값을 조회한 뒤
     * 실행이 멈췄다가 재개되는 사이 오래된 시각으로 lease를 인계하는 일을 막는다.
     */
    int acquireExpiredLease(@Param("execution") DailyBatchExecution execution,
                            @Param("leaseDurationSeconds") long leaseDurationSeconds);

    /**
     * heartbeat. owner_id + lease_token + status='RUNNING' + lease_until 유효성을 모두 만족해야
     * lease_until이 연장된다(fencing). 영향받은 row 수가 0이면 소유권을 잃은 것이다.
     */
    int renewLease(@Param("id") Long id, @Param("ownerId") String ownerId,
                   @Param("leaseToken") String leaseToken,
                   @Param("leaseDurationSeconds") long leaseDurationSeconds);

    /**
     * 완료 처리(SUCCESS/PARTIAL_FAILED/FAILED). {@link #renewLease}와 동일한 fencing 조건을 사용한다.
     */
    int completeIfOwner(@Param("id") Long id, @Param("ownerId") String ownerId,
                        @Param("leaseToken") String leaseToken, @Param("status") String status,
                        @Param("errorSummary") String errorSummary);

    DailyBatchExecution findByJobNameAndBusinessDate(@Param("jobName") String jobName,
                                                      @Param("businessDate") LocalDate businessDate);

}
