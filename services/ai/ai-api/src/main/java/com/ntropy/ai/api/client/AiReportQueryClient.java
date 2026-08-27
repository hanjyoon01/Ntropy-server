package com.ntropy.ai.api.client;

import com.ntropy.ai.api.dto.AiReportSummary;
import java.util.List;

/**
 * ai-service가 소유한 AI 리포트를 다른 모듈에서 조회하기 위한 공통 인터페이스입니다.
 *
 * BFF는 이 인터페이스에만 의존합니다.
 * 실제 AI_REPORT DB 조회 구현은 ai-service의 LocalAiReportQueryClient가 담당합니다.
 */
public interface AiReportQueryClient {

    /**
     * 특정 사용자의 특정 월 AI 리포트를 조회합니다.
     *
     * @param userId    로그인한 사용자 ID
     * @param yearMonth 조회 대상 연월. 예: "2026-08"
     * @return 조회된 AI 리포트 요약 DTO
     */
    AiReportSummary findByUserIdAndYearMonth(
            Long userId,
            String yearMonth
    );

    /**
     * 특정 사용자의 전체 AI 리포트 목록을 최신 연월순으로 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 AI 리포트 목록
     */
    List<AiReportSummary> findAllByUserId(Long userId);
}
