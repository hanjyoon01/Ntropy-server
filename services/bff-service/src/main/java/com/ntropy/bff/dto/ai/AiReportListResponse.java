package com.ntropy.bff.dto.ai;

import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.ai.api.dto.AiReportSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 사용자의 전체 AI 리포트 목록 조회 응답 DTO입니다.
 *
 * 프론트엔드는 totalCount로 리포트 존재 여부를 판단하고,
 * reports 목록을 최신 연월순으로 화면에 표시합니다.
 */
@Getter
@AllArgsConstructor
public class AiReportListResponse {

    // 사용자가 보유한 전체 AI 리포트 개수입니다.
    private final int totalCount;

    // 목록 화면에 표시할 AI 리포트 항목 목록입니다.
    private final List<AiReportListItemResponse> reports;

    /**
     * ai-service에서 전달받은 AI 리포트 목록을
     * 프론트엔드 목록 전용 응답 DTO로 변환합니다.
     *
     * 리포트가 없는 사용자는 빈 목록과 0을 정상 응답으로 반환합니다.
     *
     * @param summaries ai-service에서 조회한 AI 리포트 목록
     * @return 프론트엔드에 전달할 전체 AI 리포트 목록 응답
     */
    public static AiReportListResponse from(List<AiReportSummary> summaries) {
        // 공통 DTO 목록의 각 항목을 프론트엔드 목록 항목 DTO로 변환합니다.
        List<AiReportListItemResponse> reports = summaries.stream()
                .map(AiReportListItemResponse::from)
                .collect(Collectors.toList());

        // 변환된 목록의 개수와 목록 데이터를 함께 반환합니다.
        return new AiReportListResponse(
                reports.size(),
                reports
        );
    }
}