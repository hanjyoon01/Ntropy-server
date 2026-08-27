package com.ntropy.ai.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * AI 리포트 조회 결과를 ai-service에서 BFF로 전달하기 위한 공통 DTO입니다.
 *
 * AI_REPORT 테이블의 JSON 컬럼은 String으로 전달하지 않고 JsonNode로 전달합니다.
 * 이렇게 해야 프론트엔드 응답에서 JSON이 이스케이프된 문자열이 아니라
 * 중첩 객체 형태로 내려갑니다.
 */
public record AiReportSummary(

        // AI_REPORT.report_id
        Long reportId,

        // AI_REPORT.user_id
        // 서비스 내부 조회 계약에서만 사용하고, 프론트 응답에는 노출하지 않습니다.
        Long userId,

        // AI_REPORT.year_month
        // 예: "2026-08"
        String yearMonth,

        // AI_REPORT.financial_summary_json
        // 예: 총수입, 총지출, 가용자금 등의 재무 요약 정보
        JsonNode financialSummary,

        // AI_REPORT.recommendation_json
        // 예: 추천 금융상품, 추천 사유, 예상 효과 등의 추천 정보
        JsonNode recommendation,

        // AI_REPORT.created_at
        LocalDateTime createdAt
) {
}
