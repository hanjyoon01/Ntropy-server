package com.ntropy.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI_REPORT 테이블과 1:1 매핑되는 Domain 엔티티 클래스
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiReport {

    /** AI 리포트 PK (AUTO_INCREMENT) */
    private Long reportId;

    /** 회원 고유 ID (user-service 참조용) */
    private Long userId;

    /** 리포트 대상 연월 (예: "2026-08") */
    private String yearMonth;

    /** 재무 진단 스냅샷 데이터 (JSON 문자열) */
    private String financialSummaryJson;

    /** AI 추천 상품 및 추천 사유 데이터 (JSON 문자열) */
    private String recommendationJson;

    /** 리포트 생성 시각 */
    private LocalDateTime createdAt;
}