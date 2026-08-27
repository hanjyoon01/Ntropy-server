package com.ntropy.ai.api.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.common.util.JsonNamingConverter;

/** 상세 API와 PDF가 함께 사용하는 사용자 표시 기준의 AI 리포트 모델. */
public record AiReportDetailSummary(
        Long reportId,
        String yearMonth,
        JsonNode financialSummary,
        JsonNode recommendation,
        LocalDateTime createdAt
) {
    public static AiReportDetailSummary from(AiReportSummary summary) {
        return new AiReportDetailSummary(
                summary.reportId(),
                summary.yearMonth(),
                JsonNamingConverter.toCamelCase(summary.financialSummary()),
                JsonNamingConverter.toCamelCase(summary.recommendation()),
                summary.createdAt()
        );
    }
}
