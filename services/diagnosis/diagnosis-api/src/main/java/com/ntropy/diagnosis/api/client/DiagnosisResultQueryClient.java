package com.ntropy.diagnosis.api.client;

import com.ntropy.diagnosis.api.dto.DiagnosisResultSummary;

/**
 * 월별 재무진단 결과를 다른 모듈에 제공하는 diagnosis-service 계약입니다.
 *
 * bff-service가 이 인터페이스만 호출하고, 실제 조회와 DTO 변환은
 * diagnosis-service의 구현체가 담당합니다.
 */
public interface DiagnosisResultQueryClient {

    DiagnosisResultSummary findByUserIdAndYearMonth(Long userId, String yearMonth);
}
