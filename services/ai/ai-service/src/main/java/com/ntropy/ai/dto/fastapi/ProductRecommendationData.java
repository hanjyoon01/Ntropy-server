package com.ntropy.ai.dto.fastapi;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI의 FinancialProductSchema와 매핑되는
 * 추천 금융상품 상세 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendationData {

    /**
     * 금융상품 고유 식별자입니다.
     */
    @JsonProperty("product_id")
    private String productId;

    /**
     * 금융상품명입니다.
     */
    @JsonProperty("product_name")
    private String productName;

    /**
     * 상품 유형입니다.
     *
     * 예: CARD, SAVINGS
     */
    @JsonProperty("product_type")
    private String productType;

    /**
     * 상품 제공 금융사입니다.
     */
    private String provider;

    /**
     * 상품의 핵심 혜택 요약입니다.
     */
    private String summary;

    /**
     * 주요 추천 대상 고객군입니다.
     */
    @JsonProperty("target_group")
    private String targetGroup;

    /**
     * N잡 관련 활용 팁입니다.
     */
    @JsonProperty("njob_trend_tip")
    private String njobTrendTip;

    /**
     * 상품 유형별 추가 상세 정보입니다.
     */
    private Map<String, Object> details;
}