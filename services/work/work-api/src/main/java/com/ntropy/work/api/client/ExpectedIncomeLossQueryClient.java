package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * work-service가 구현해야 하는 방어모드용 예상 손실소득 조회 계약.
 *
 * <p>방어모드는 시작일과 종료일을 전달하고, work-service는 요청 기간과
 * 겹치는 예상 손실소득을 잡별로 집계해 반환한다. 미래 WORK_LOG가 사전에 생성되어
 * 있다는 것을 전제하지 않고, 정산 방식과 정기 스케줄을 활용해 work-service에서 계산한다.</p>
 */
public interface ExpectedIncomeLossQueryClient {

    /** 인증된 사용자의 요청 기간 내 예상 손실소득을 잡별로 조회한다. */
    List<JobExpectedIncomeLossSummary> findExpectedIncomeLossByJob(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate);
}
