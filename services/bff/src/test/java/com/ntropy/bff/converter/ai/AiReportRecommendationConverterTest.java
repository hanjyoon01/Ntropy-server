package com.ntropy.bff.converter.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.bff.dto.ai.AiReportRecommendationResponse;
import com.ntropy.bff.dto.ai.ProductDetailsResponse;
import com.ntropy.bff.dto.ai.RecommendedProductResponse;

class AiReportRecommendationConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsPastSnakeCaseAndNestedAliases() throws Exception {
        JsonNode source = objectMapper.readTree("{\n"
                + "  \"recommended_product\": {\n"
                + "    \"product_id\": \"P-1\",\n"
                + "    \"product_name\": \"미래 적금\",\n"
                + "    \"product_type\": \"SAVINGS\",\n"
                + "    \"target_group\": \"N잡러\",\n"
                + "    \"njob_trend_tip\": \"자동이체\",\n"
                + "    \"details\": {\n"
                + "      \"selected_option\": {\"base_interest_rate\": \"3.75\", \"term_months\": \"12.0\"},\n"
                + "      \"max_monthly_amount\": \"500000\"\n"
                + "    }\n"
                + "  },\n"
                + "  \"simulated_extra_income\": \"120000\",\n"
                + "  \"financial_activity_insight\": \"안정적\",\n"
                + "  \"financial_type\": \"저축 여력형\",\n"
                + "  \"job_insight\": \"유지\",\n"
                + "  \"future_income_trend\": \"상승\"\n"
                + "}");

        AiReportRecommendationResponse result = AiReportRecommendationConverter.convert(source);

        assertEquals("SURPLUS", result.getFinancialType());
        assertEquals(120000L, result.getSimulatedExtraIncome());
        assertEquals("안정적", result.getFinancialActivityInsight());
        RecommendedProductResponse product = result.getRecommendedProduct();
        assertEquals("P-1", product.getProductId());
        assertEquals("미래 적금", product.getProductName());
        assertEquals("SAVINGS", product.getProductType());
        assertEquals("N잡러", product.getTargetGroup());
        assertEquals("자동이체", product.getNjobTrendTip());
        assertEquals(new BigDecimal("3.75"), product.getDetails().getInterestRate());
        assertEquals(12, product.getDetails().getSavingPeriod());
        assertEquals(500000L, product.getDetails().getMaxMonthlyAmount());
    }

    @Test
    void prefersStandardFieldsOverLegacyAliases() throws Exception {
        JsonNode source = objectMapper.readTree("{\n"
                + "  \"financialType\": \"BALANCED\",\n"
                + "  \"financial_type\": \"소비 압박형\",\n"
                + "  \"recommendedProduct\": {\n"
                + "    \"productName\": \"표준 상품\",\n"
                + "    \"product_name\": \"과거 상품\",\n"
                + "    \"details\": {\n"
                + "      \"interestRate\": 4.1,\n"
                + "      \"interest_rate\": 9.9,\n"
                + "      \"savingPeriod\": 24,\n"
                + "      \"termMonths\": 36,\n"
                + "      \"save_period_month\": 48\n"
                + "    }\n"
                + "  }\n"
                + "}");

        AiReportRecommendationResponse result = AiReportRecommendationConverter.convert(source);

        assertEquals("BALANCED", result.getFinancialType());
        assertEquals("표준 상품", result.getRecommendedProduct().getProductName());
        assertEquals(new BigDecimal("4.1"), result.getRecommendedProduct().getDetails().getInterestRate());
        assertEquals(24, result.getRecommendedProduct().getDetails().getSavingPeriod());
    }

    @Test
    void returnsNullProductButStableEmptyDetailsForExistingProduct() throws Exception {
        AiReportRecommendationResponse noProduct = AiReportRecommendationConverter.convert(
                objectMapper.readTree("{\"recommendedProduct\":null}")
        );
        assertNull(noProduct.getRecommendedProduct());

        AiReportRecommendationResponse noDetails = AiReportRecommendationConverter.convert(
                objectMapper.readTree("{\"recommendedProduct\":{\"productName\":\"상품\",\"details\":null}}")
        );
        ProductDetailsResponse details = noDetails.getRecommendedProduct().getDetails();
        assertNotNull(details);
        assertNull(details.getInterestRate());
        assertNull(details.getSavingPeriod());
        assertNull(details.getMaxMonthlyAmount());
    }

    @Test
    void acceptsDecimalsForRateAndOnlyExactIntegersForIntegerFields() throws Exception {
        JsonNode source = objectMapper.readTree("{\"recommendedProduct\":{\"details\":{"
                + "\"interestRate\":\"12.5\","
                + "\"savingPeriod\":\"12.0\","
                + "\"maxMonthlyAmount\":12.0}}}");

        ProductDetailsResponse details = AiReportRecommendationConverter.convert(source)
                .getRecommendedProduct().getDetails();

        assertEquals(new BigDecimal("12.5"), details.getInterestRate());
        assertEquals(12, details.getSavingPeriod());
        assertEquals(12L, details.getMaxMonthlyAmount());
    }

    @Test
    void readsFlatLegacyDetailAliases() throws Exception {
        JsonNode source = objectMapper.readTree("{\"recommended_product\":{\"details\":{"
                + "\"interest_rate\":4,"
                + "\"save_period_month\":\"18\","
                + "\"max_monthly_amount\":\"300000.0\"}}}");

        ProductDetailsResponse details = AiReportRecommendationConverter.convert(source)
                .getRecommendedProduct().getDetails();

        assertEquals(new BigDecimal("4"), details.getInterestRate());
        assertEquals(18, details.getSavingPeriod());
        assertEquals(300000L, details.getMaxMonthlyAmount());
    }

    @Test
    void rejectsFractionsInvalidTypesAndOverflowWithoutRounding() throws Exception {
        JsonNode fractions = objectMapper.readTree("{\"recommendedProduct\":{\"details\":{"
                + "\"interestRate\":true,"
                + "\"savingPeriod\":12.5,"
                + "\"maxMonthlyAmount\":\"12.5\"}}}");
        ProductDetailsResponse fractionDetails = AiReportRecommendationConverter.convert(fractions)
                .getRecommendedProduct().getDetails();
        assertNull(fractionDetails.getInterestRate());
        assertNull(fractionDetails.getSavingPeriod());
        assertNull(fractionDetails.getMaxMonthlyAmount());

        JsonNode overflow = objectMapper.readTree("{\"recommendedProduct\":{\"details\":{"
                + "\"interestRate\":\"not-a-number\","
                + "\"savingPeriod\":\"2147483648\","
                + "\"maxMonthlyAmount\":\"9223372036854775808\"}}}");
        ProductDetailsResponse overflowDetails = AiReportRecommendationConverter.convert(overflow)
                .getRecommendedProduct().getDetails();
        assertNull(overflowDetails.getInterestRate());
        assertNull(overflowDetails.getSavingPeriod());
        assertNull(overflowDetails.getMaxMonthlyAmount());
    }

    @Test
    void normalizesFinancialTypesAndRejectsUnsupportedValues() {
        assertFinancialType("SURPLUS", "SURPLUS");
        assertFinancialType("BALANCED", "BALANCED");
        assertFinancialType("DEFICIT", "DEFICIT");
        assertFinancialType("저축 여력형", "SURPLUS");
        assertFinancialType("균형 관리형", "BALANCED");
        assertFinancialType("가용자금 관리형", "BALANCED");
        assertFinancialType("소비 압박형", "DEFICIT");
        assertFinancialType("현금흐름 위험형", "DEFICIT");
        assertFinancialType("소득 데이터 부족형", "BALANCED");
        assertFinancialType(" ", null);
        assertFinancialType("UNKNOWN", null);
        assertNull(AiReportRecommendationConverter.convert(objectMapper.createObjectNode())
                .getFinancialType());
    }

    private void assertFinancialType(String input, String expected) {
        JsonNode source = objectMapper.createObjectNode().put("financialType", input);
        assertEquals(expected, AiReportRecommendationConverter.convert(source).getFinancialType());
    }
}
