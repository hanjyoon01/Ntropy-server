package com.ntropy.diagnosis.api.client;

import java.time.YearMonth;

import com.ntropy.diagnosis.api.dto.DiagnosisAnalysisSummary;

/**
 * AI-service가 diagnosis-service에 회원·연월 단위 재무진단 상세를 조회하는 계약.
 *
 * <p>현재 월 재계산 직후 또는 과거월 최초 확정 직후, AI 보고서를 생성할 입력을
 * 모으는 용도로만 사용한다. 이미 확정된 과거월의 보고서를 다시 생성하려고
 * 재호출하지 않는다 — 카테고리별 소비는 조회 시점에 다시 집계하므로, 확정
 * 이후 재조회하면 고정된 스칼라 값과 어긋날 수 있다. 확정된 월의 보고서를
 * 나중에 다시 봐야 한다면 AI-service가 생성 시점에 저장해 둔 결과를 쓴다.</p>
 */
public interface DiagnosisAnalysisQueryClient {

    /** 인증된 사용자의 회원·연월 재무진단 상세를 조회한다. 결과가 없으면 예외를 던진다. */
    DiagnosisAnalysisSummary getMonthlyAnalysis(Long userId, YearMonth yearMonth);
}
