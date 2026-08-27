package com.ntropy.ai.client.fastapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ntropy.ai.dto.fastapi.ProductRecommendationRequest;
import com.ntropy.ai.dto.fastapi.ProductRecommendationResponse;
import com.ntropy.ai.config.FastApiProperties;

/**
 * FastAPI 금융상품 추천 API Client입니다.
 */
@Component
public class FastApiProductRecommendationClient {

    private final RestTemplate restTemplate;
    private final String fastApiBaseUrl;

    @Autowired
    public FastApiProductRecommendationClient(
            FastApiProperties properties
    ) {
        this(properties.getBaseUrl());
    }

    protected FastApiProductRecommendationClient(String fastApiBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.fastApiBaseUrl = fastApiBaseUrl;
    }

    /**
     * FastAPI 추천 API를 호출합니다.
     */
    public ProductRecommendationResponse recommend(
            ProductRecommendationRequest request
    ) {
        String url = fastApiBaseUrl
                + "/api/v1/recommend";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ProductRecommendationRequest> entity =
                new HttpEntity<>(request, headers);

        return restTemplate.postForObject(
                url,
                entity,
                ProductRecommendationResponse.class
        );
    }
}