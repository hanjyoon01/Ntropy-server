package com.ntropy.diagnosis.api.client;

import com.ntropy.diagnosis.api.dto.DiagnosisDefenseSnapshot;

/**
 * diagnosis-service가 구현해야 하는 방어모드용 재무진단 조회 계약.
 *
 * <p>최신 재무진단의 리저브, 안전자산과 최근 최대 3개 total_expense의 평균을
 * 반환한다. 유동자산 합계, D-Day, 일평균 지출 및 계산 상태는
 * defense-service가 계산하므로 이 구현에서 만들지 않는다.</p>
 */
public interface DiagnosisQueryClient {

    /** 인증된 사용자의 D-Day 계산용 재무 원본 요약을 조회한다. */
    DiagnosisDefenseSnapshot getDefenseSnapshot(Long userId);
}
