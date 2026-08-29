package com.ntropy.bff.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 리포트 상세 화면에 반환할 추천 금융 상품입니다. */
@Getter
@AllArgsConstructor
public class RecommendedProductResponse {
    private final String productId;
    private final String productName;
    private final String productType;
    private final String provider;
    private final String summary;
    private final String targetGroup;
    private final String njobTrendTip;
    private final ProductDetailsResponse details;
}
