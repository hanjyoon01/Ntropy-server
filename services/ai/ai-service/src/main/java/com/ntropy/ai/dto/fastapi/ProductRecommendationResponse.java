package com.ntropy.ai.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI가 반환하는 월간 AI 리포트 응답입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendationResponse {

    /**
     * 추천 금융상품입니다.
     */
    @JsonProperty("recommended_product")
    private ProductRecommendationData recommendedProduct;

    /**
     * 예상 추가 수익 또는 절감 금액입니다.
     */
    @JsonProperty("simulated_extra_income")
    private Long simulatedExtraIncome;

    /**
     * 금융상품 추천 사유입니다.
     */
    private String reasoning;

    /**
     * 소비·소득 활동에 대한 요약 인사이트입니다.
     */
    @JsonProperty("financial_activity_insight")
    private String financialActivityInsight;

    /**
     * 사용자의 재무 유형입니다.
     */
    @JsonProperty("financial_type")
    private String financialType;

    /**
     * 잡 관련 사용자 요약 인사이트입니다.
     */
    @JsonProperty("job_insight")
    private String jobInsight;

    /**
     * 미래 소득 트렌드 및 N잡 코칭 문구입니다.
     */
    @JsonProperty("future_income_trend")
    private String futureIncomeTrend;
}