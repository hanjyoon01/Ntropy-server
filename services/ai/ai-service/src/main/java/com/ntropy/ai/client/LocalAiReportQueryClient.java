package com.ntropy.ai.client;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.domain.AiReport;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.ai.service.AiReportService;
import com.ntropy.ai.api.client.AiReportQueryClient;
import com.ntropy.ai.api.dto.AiReportSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/**
 * AiReportQueryClient의 ai-service 내부 구현체입니다.
 *
 * BFF는 AiReportQueryClient 인터페이스만 호출하고,
 * 실제 AI_REPORT 조회와 JSON 변환은 이 클래스가 담당합니다.
 */
@Component
@RequiredArgsConstructor
public class LocalAiReportQueryClient implements AiReportQueryClient {

    // AI_REPORT 조회 비즈니스 로직을 담당하는 Service입니다.
    private final AiReportService aiReportService;

    // DB의 JSON 문자열을 JsonNode 객체로 변환할 때 사용합니다.
    private final ObjectMapper objectMapper;

    /**
     * 사용자와 조회 연월을 기준으로 AI 리포트 한 건을 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @param yearMonth 조회 대상 연월. 예: "2026-07"
     * @return BFF에 전달할 공통 AI 리포트 DTO
     */
    @Override
    public AiReportSummary findByUserIdAndYearMonth(
            Long userId,
            String yearMonth
    ) {
        // Service를 통해 DB에서 AI_REPORT 데이터를 조회합니다.
        AiReport aiReport = aiReportService.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );

        // 조회한 Domain 객체를 BFF와 공유하는 공통 DTO로 변환합니다.
        return toAiReportSummary(aiReport);
    }

    /**
     * 특정 사용자의 전체 AI 리포트 목록을 최신 연월순으로 조회합니다.
     *
     * 리포트가 없는 신규 사용자는 빈 List를 정상 반환합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return BFF에 전달할 공통 AI 리포트 DTO 목록
     */
    @Override
    public List<AiReportSummary> findAllByUserId(Long userId) {
        // Service에서 최신 연월순으로 조회한 Domain 목록을 받습니다.
        List<AiReport> aiReports = aiReportService.findAllByUserId(userId);

        // 각 Domain 객체를 BFF가 사용할 공통 DTO로 변환합니다.
        return aiReports.stream()
                .map(this::toAiReportSummary)
                .collect(Collectors.toList());
    }

    /**
     * AI_REPORT Domain 객체를 모듈 간 전달용 공통 DTO로 변환합니다.
     *
     * @param aiReport DB에서 조회한 AI 리포트 Domain 객체
     * @return JSON 문자열까지 변환된 공통 AI 리포트 DTO
     */
    private AiReportSummary toAiReportSummary(AiReport aiReport) {
        return new AiReportSummary(
                aiReport.getReportId(),
                aiReport.getUserId(),
                aiReport.getYearMonth(),
                readJsonOrEmptyObject(
                        aiReport.getFinancialSummaryJson(),
                        "financial_summary_json"
                ),
                readJsonOrEmptyObject(
                        aiReport.getRecommendationJson(),
                        "recommendation_json"
                ),
                aiReport.getCreatedAt()
        );
    }

    /**
     * DB의 JSON 문자열을 JsonNode 객체로 변환합니다.
     *
     * JSON 컬럼 값이 null 또는 빈 문자열이면,
     * 프론트엔드가 다루기 쉽도록 빈 객체 {}를 반환합니다.
     *
     * @param jsonText DB에 저장된 JSON 문자열
     * @param columnName 오류 메시지에 표시할 DB 컬럼명
     * @return 변환된 JsonNode 또는 빈 JSON 객체
     */
    private JsonNode readJsonOrEmptyObject(
            String jsonText,
            String columnName
    ) {
        // 아직 값이 없는 목데이터도 정상 조회되도록 빈 객체를 반환합니다.
        if (jsonText == null || jsonText.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(jsonText);
        } catch (IOException exception) {
            throw new ServiceException(
                    AiReportErrorCode.REPORT_JSON_INVALID,
                    columnName + " 컬럼을 JSON으로 변환할 수 없습니다."
            );
        }
    }
}