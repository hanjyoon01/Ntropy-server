package com.ntropy.account.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.entity.AccountSyncState;

@Mapper
public interface AccountSyncStateMapper {

    AccountSyncState findByConnectionAndOrganization(@Param("codefConnectionId") Long codefConnectionId,
                                                      @Param("organizationCode") String organizationCode);

    /** watermark 행이 없으면 PENDING 상태로 생성한다. 이미 있으면 아무 것도 바꾸지 않는다. */
    void insertIfAbsent(AccountSyncState state);

    /**
     * 호출 시점에 job_name/business_date에 대해 유효한 lease(owner_id + lease_token +
     * status='RUNNING' + lease_until 유효)를 가진 실행에서만 watermark를 전진시킨다 (fencing).
     * 영향받은 row 수가 0이면 watermark 갱신 실패로 처리해야 한다. 성공 시각과 lease 유효성은
     * 같은 UPDATE 문 안에서 DB {@code NOW()}로 평가한다.
     */
    int advanceIfOwner(@Param("codefConnectionId") Long codefConnectionId,
                       @Param("organizationCode") String organizationCode,
                       @Param("lastStatus") String lastStatus,
                       @Param("lastErrorCode") String lastErrorCode,
                       @Param("jobName") String jobName,
                       @Param("businessDate") LocalDate businessDate,
                       @Param("ownerId") String ownerId,
                       @Param("leaseToken") String leaseToken);

    /** 성공 watermark는 유지한 채, 현재 lease 소유자만 기관 실패 상태를 기록한다. */
    int markStatusIfOwner(@Param("codefConnectionId") Long codefConnectionId,
                          @Param("organizationCode") String organizationCode,
                          @Param("lastStatus") String lastStatus,
                          @Param("lastErrorCode") String lastErrorCode,
                          @Param("jobName") String jobName,
                          @Param("businessDate") LocalDate businessDate,
                          @Param("ownerId") String ownerId,
                          @Param("leaseToken") String leaseToken);
}
