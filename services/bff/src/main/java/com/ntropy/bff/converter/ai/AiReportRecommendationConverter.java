package com.ntropy.bff.converter.ai;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.bff.dto.ai.AiReportRecommendationResponse;
import com.ntropy.bff.dto.ai.ProductDetailsResponse;
import com.ntropy.bff.dto.ai.RecommendedProductResponse;

/** 저장 시점별로 다른 추천 JSON을 프론트엔드의 단일 계약으로 변환합니다. */
public final class AiReportRecommendationConverter {

    private AiReportRecommendationConverter() {
    }

    public static AiReportRecommendationResponse convert(JsonNode recommendation) {
        JsonNode source = objectOrNull(recommendation);
        return new AiReportRecommendationResponse(
                convertProduct(first(source, "recommendedProduct", "recommended_product")),
                toExactLong(first(source, "simulatedExtraIncome", "simulated_extra_income")),
                toStringValue(first(source, "reasoning")),
                toStringValue(first(source, "financialActivityInsight", "financial_activity_insight")),
                toFinancialType(first(source, "financialType", "financial_type")),
                toStringValue(first(source, "jobInsight", "job_insight")),
                toStringValue(first(source, "futureIncomeTrend", "future_income_trend"))
        );
    }

    private static RecommendedProductResponse convertProduct(JsonNode productNode) {
        JsonNode product = objectOrNull(productNode);
        if (product == null) {
            return null;
        }
        return new RecommendedProductResponse(
                toStringValue(first(product, "productId", "product_id")),
                toStringValue(first(product, "productName", "product_name")),
                toStringValue(first(product, "productType", "product_type")),
                toStringValue(first(product, "provider")),
                toStringValue(first(product, "summary")),
                toStringValue(first(product, "targetGroup", "target_group")),
                toStringValue(first(product, "njobTrendTip", "njob_trend_tip")),
                convertDetails(first(product, "details"))
        );
    }

    private static ProductDetailsResponse convertDetails(JsonNode detailsNode) {
        JsonNode details = objectOrNull(detailsNode);
        JsonNode selectedOption = objectOrNull(first(details, "selectedOption", "selected_option"));
        return new ProductDetailsResponse(
                toBigDecimal(firstPresent(
                        first(details, "interestRate", "interest_rate"),
                        first(selectedOption, "baseInterestRate", "base_interest_rate")
                )),
                toExactInteger(firstPresent(
                        first(details, "savingPeriod", "saving_period", "termMonths", "term_months",
                                "savePeriodMonth", "save_period_month"),
                        first(selectedOption, "termMonths", "term_months")
                )),
                toExactLong(first(details, "maxMonthlyAmount", "max_monthly_amount"))
        );
    }

    /** 선언된 순서대로 가장 먼저 존재하는 필드를 선택합니다. */
    private static JsonNode first(JsonNode object, String... fieldNames) {
        if (object == null || !object.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (object.has(fieldName)) {
                return object.get(fieldName);
            }
        }
        return null;
    }

    private static JsonNode firstPresent(JsonNode primary, JsonNode fallback) {
        return primary != null ? primary : fallback;
    }

    private static JsonNode objectOrNull(JsonNode node) {
        return node != null && node.isObject() ? node : null;
    }

    private static String toStringValue(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.textValue().trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                String value = node.textValue().trim();
                return value.isEmpty() ? null : new BigDecimal(value);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static Integer toExactInteger(JsonNode node) {
        BigDecimal value = toBigDecimal(node);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private static Long toExactLong(JsonNode node) {
        BigDecimal value = toBigDecimal(node);
        if (value == null) {
            return null;
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private static String toFinancialType(JsonNode node) {
        String value = toStringValue(node);
        if (value == null) {
            return null;
        }
        switch (value) {
            case "SURPLUS":
            case "BALANCED":
            case "DEFICIT":
                return value;
            case "저축 여력형":
                return "SURPLUS";
            case "균형 관리형":
            case "가용자금 관리형":
            case "소득 데이터 부족형":
                return "BALANCED";
            case "소비 압박형":
            case "현금흐름 위험형":
                return "DEFICIT";
            default:
                return null;
        }
    }
}
