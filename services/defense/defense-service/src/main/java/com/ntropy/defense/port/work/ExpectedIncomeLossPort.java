package com.ntropy.defense.port.work;

import java.time.LocalDate;
import java.util.List;

/** defense-service가 정의한, work-service의 예상 손실소득 조회 포트. */
@FunctionalInterface
public interface ExpectedIncomeLossPort {

    List<JobExpectedIncomeLoss> findExpectedIncomeLossByJob(Long userId, LocalDate fromDate, LocalDate toDate);
}
