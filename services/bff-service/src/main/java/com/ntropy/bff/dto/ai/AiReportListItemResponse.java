package com.ntropy.bff.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.ai.api.dto.AiReportSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 리포트 목록 화면의 리포트 카드 한 건을 표현하는 응답 DTO입니다.
 * 프론트엔드는 이 DTO를 이용해 아래와 같은 정보를 표시합니다.
 * 6월 리포트
 * 소득 372만원 · 소비 276만원
 */
@Getter
@AllArgsConstructor
public class AiReportListItemResponse {

    // AI 리포트 고유 ID입니다.
    private final Long reportId;

    // 리포트 대상 연월입니다. 예: "2026-06"
    private final String yearMonth;

    // 화면 표시용 제목입니다. 예: "2026년 6월 리포트"
    private final String reportTitle;

    // 해당 월의 총소득입니다. 원 단위입니다.
    private final Long totalIncome;

    // 해당 월의 총소비입니다. 원 단위입니다.
    private final Long totalExpense;

    // 배열이 아닌 ISO 날짜·시간 문자열로 응답하기 위한 설정입니다.
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss"
    )
    private final LocalDateTime createdAt;

    /**
     * ai-service가 반환한 공통 DTO를
     * 프론트엔드 목록 화면 전용 DTO로 변환합니다.
     *
     * @param summary ai-service에서 조회한 AI 리포트 요약 데이터
     * @return 프론트엔드 목록 카드에 사용할 리포트 DTO
     */
    public static AiReportListItemResponse from(AiReportSummary summary) {
        // "2026-06"을 "-" 기준으로 나눠 화면 표시용 제목을 생성합니다.
        String[] yearMonthParts = summary.yearMonth().split("-");

        String reportTitle = yearMonthParts[0]
                + "년 "
                + Integer.parseInt(yearMonthParts[1])
                + "월 리포트";

        // AI_REPORT.financial_summary_json을 JSON 객체로 가져옵니다.
        JsonNode financialSummary = summary.financialSummary();

        // JSON 내부의 totalIncome, totalExpense 값을 꺼냅니다.
        // 이전 목데이터에 필드가 없더라도 목록 조회 자체가 실패하지 않도록
        // 값이 없으면 0원을 반환합니다.
        long totalIncome = getLongOrZero(
                financialSummary,
                "totalIncome"
        );

        long totalExpense = getLongOrZero(
                financialSummary,
                "totalExpense"
        );

        return new AiReportListItemResponse(
                summary.reportId(),
                summary.yearMonth(),
                reportTitle,
                totalIncome,
                totalExpense,
                summary.createdAt()
        );
    }

    /**
     * JSON 객체에서 숫자 필드를 안전하게 읽습니다.
     *
     * @param jsonNode financial_summary_json에 해당하는 JSON 객체
     * @param fieldName 읽을 필드명
     * @return 숫자 값. 값이 없거나 JSON이 비어 있으면 0
     */
    private static long getLongOrZero(
            JsonNode jsonNode,
            String fieldName
    ) {
        if (jsonNode == null || jsonNode.isNull()) {
            return 0L;
        }

        return jsonNode.path(fieldName).asLong(0L);
    }
}