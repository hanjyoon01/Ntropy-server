package com.ntropy.defense.port.diagnosis;

import java.time.YearMonth;

/** defense-service가 정의한, diagnosis-service의 재무진단 재계산 요청 포트. */
@FunctionalInterface
public interface DiagnosisRecalculationPort {

    void recalculate(Long userId, YearMonth yearMonth);
}
