package com.ntropy.diagnosis.api.client;

import java.time.YearMonth;

/**
 * AI-service가 diagnosis-service에 회원·연월 단위 재무진단 재계산을 요청하는 계약.
 *
 * <p>현재 월은 호출할 때마다 임시 스냅샷으로 다시 계산한다. 완료된 과거월은
 * 최초 호출 시 월말 기준 자산으로 계산하고 즉시 확정하며, 이미 확정된
 * 과거월에 대한 재호출은 아무 것도 바꾸지 않고 조용히 종료한다. 미래
 * 연월은 요청 자체를 거부한다.</p>
 */
public interface DiagnosisCommandClient {

    /**
     * 인증된 사용자의 회원·연월 재무진단을 재계산한다.
     *
     * <p>결과는 반환하지 않는다. 성공은 정상 리턴, 실패는 예외로만 전달하며
     * 계산된 값은 {@link DiagnosisAnalysisQueryClient}로 별도 조회한다.</p>
     */
    void recalculate(Long userId, YearMonth yearMonth);
}
