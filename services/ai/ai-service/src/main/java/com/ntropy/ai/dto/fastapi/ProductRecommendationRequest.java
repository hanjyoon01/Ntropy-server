package com.ntropy.ai.dto.fastapi;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI-service가 FastAPI 추천 API에 전달하는 요청 DTO입니다.
 * 원천 거래 전체가 아니라,
 * 월별 소득·소비 집계 결과와 AI 추천에 필요한 요약 데이터만 전달합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendationRequest {

    /**
     * 추천 대상 사용자 ID입니다.
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * 추천 대상 연월입니다.
     *
     * 예: 2026-07
     */
    @JsonProperty("year_month")
    private String yearMonth;

    /**
     * 해당 월의 확정 총소득입니다.
     */
    @JsonProperty("total_income")
    private Long totalIncome;

    /**
     * 해당 월의 총소비입니다.
     */
    @JsonProperty("total_expense")
    private Long totalExpense;

    /**
     * 총소득에서 총소비를 뺀 가용금액입니다.
     */
    @JsonProperty("available_funds")
    private Long availableFunds;

    /**
     * 카테고리별 소비 금액 목록입니다.
     *
     * Java에서는 List를 JSON 문자열로 변환해 전달합니다.
     */
    @JsonProperty("category_expenses")
    private String categoryExpenses;

    /**
     * 전월 총소득입니다.
     */
    @JsonProperty("previous_month_income")
    private Long previousMonthIncome;

    /**
     * 전월 대비 소득 증감액입니다.
     */
    @JsonProperty("income_change_amount")
    private Long incomeChangeAmount;

    /**
     * 전월 대비 소득 증감률입니다.
     *
     * 퍼센트가 아니라 0~1 사이 비율입니다.
     * 예: 0.25 = 25%
     */
    @JsonProperty("income_change_rate")
    private Double incomeChangeRate;

    /**
     * 최근 소득 변동성입니다.
     */
    @JsonProperty("income_volatility")
    private Double incomeVolatility;

    /**
     * FastAPI가 잡 관련 AI 인사이트를 생성할 때 사용할 입력 데이터입니다.
     *
     * 프론트 표시용 jobSummaries와 역할을 분리합니다.
     *
     * 예:
     * [
     *   {
     *     "jobId": 1,
     *     "jobName": "배달",
     *     "incomeAmount": 1000000,
     *     "incomeRatio": 0.25,
     *     "totalWorkMinutes": 2400,
     *     "workDays": 10,
     *     "averageFatigue": 3.2,
     *     "latestFatigue": 4
     *   }
     * ]
     */
    @JsonProperty("job_insight_inputs")
    private List<Map<String, Object>> jobInsightInputs;
}