package com.ntropy.bff.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 리포트 상세 화면에 반환할 추천 정보입니다. */
@Getter
@AllArgsConstructor
public class AiReportRecommendationResponse {
    private final RecommendedProductResponse recommendedProduct;
    private final Long simulatedExtraIncome;
    private final String reasoning;
    private final String financialActivityInsight;
    private final String financialType;
    private final String jobInsight;
    private final String futureIncomeTrend;
}
