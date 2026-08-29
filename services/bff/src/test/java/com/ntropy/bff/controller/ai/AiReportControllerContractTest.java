package com.ntropy.bff.controller.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.bff.exception.GlobalExceptionHandler;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.ai.api.client.AiReportEmailDeliveryClient;
import com.ntropy.ai.api.client.AiReportQueryClient;
import com.ntropy.ai.api.dto.AiReportEmailDeliverySummary;
import com.ntropy.ai.api.dto.AiReportSummary;

class AiReportControllerContractTest {

    private StubDeliveryClient deliveryClient;
    private StubAiReportQueryClient queryClient;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deliveryClient = new StubDeliveryClient();
        queryClient = new StubAiReportQueryClient();
        objectMapper = new ObjectMapper();
        AiReportController controller = new AiReportController(
                queryClient, deliveryClient, new AuthenticatedUserIdResolver()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesPostEmailDeliveryWithOnlyYearMonthRequestParameter() throws Exception {
        Method method = AiReportController.class.getDeclaredMethod(
                "deliverAiReportByEmail", Authentication.class, String.class
        );
        assertArrayEquals(new String[] {"/deliveries/email"}, method.getAnnotation(PostMapping.class).value());

        Parameter[] parameters = method.getParameters();
        assertEquals(2, parameters.length);
        assertFalse(parameters[0].isAnnotationPresent(RequestBody.class));
        RequestParam yearMonth = parameters[1].getAnnotation(RequestParam.class);
        assertEquals("", yearMonth.name());
        assertFalse(parameters[1].isAnnotationPresent(RequestBody.class));
    }

    @Test
    void usesAuthenticatedUserAndReturnsMaskedDeliveryResult() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai-reports/deliveries/email")
                        .param("yearMonth", "2026-05")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
        );
        assertEquals(42L, deliveryClient.userId);
        assertEquals("2026-05", deliveryClient.yearMonth);
        assertEquals("AI 리포트를 이메일로 전송했습니다.", body.path("message").asText());
        assertEquals("EMAIL", body.path("data").path("channel").asText());
        assertEquals("bi***@example.com", body.path("data").path("recipientEmail").asText());
    }

    @Test
    void missingYearMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/ai-reports/deliveries/email").principal(authentication()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsStandardizedDetailWhileKeepingFinancialSummaryCamelCase() throws Exception {
        queryClient.detail = new AiReportSummary(
                7L,
                42L,
                "2026-07",
                objectMapper.readTree("{\"total_income\":2300000,\"total_expense\":1200000}"),
                objectMapper.readTree("{\"financial_type\":\"가용자금 관리형\","
                        + "\"recommended_product\":{\"product_name\":\"안심 적금\","
                        + "\"details\":{\"interest_rate\":\"3.5\",\"term_months\":\"12.0\"}}}"),
                LocalDateTime.of(2026, 8, 1, 3, 0)
        );

        MvcResult result = mockMvc.perform(get("/api/ai-reports/2026-07").principal(authentication()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = responseData(result);
        assertEquals(42L, queryClient.userId);
        assertEquals("2026-07", queryClient.yearMonth);
        assertEquals(2300000L, data.path("financialSummary").path("totalIncome").asLong());
        assertEquals("BALANCED", data.path("recommendation").path("financialType").asText());
        assertEquals("안심 적금",
                data.path("recommendation").path("recommendedProduct").path("productName").asText());
        assertEquals(12,
                data.path("recommendation").path("recommendedProduct").path("details")
                        .path("savingPeriod").asInt());
    }

    @Test
    void keepsListApiContractUnchanged() throws Exception {
        queryClient.list = List.of(new AiReportSummary(
                8L,
                42L,
                "2026-06",
                objectMapper.readTree("{\"totalIncome\":2000000,\"totalExpense\":900000}"),
                objectMapper.createObjectNode(),
                LocalDateTime.of(2026, 7, 1, 3, 0)
        ));

        MvcResult result = mockMvc.perform(get("/api/ai-reports").principal(authentication()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = responseData(result);
        assertEquals(42L, queryClient.userId);
        assertEquals(1, data.path("totalCount").asInt());
        assertEquals("2026년 6월 리포트", data.path("reports").get(0).path("reportTitle").asText());
        assertEquals(2000000L, data.path("reports").get(0).path("totalIncome").asLong());
        assertEquals(900000L, data.path("reports").get(0).path("totalExpense").asLong());
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
        ).path("data");
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(42L, "unused", Collections.emptyList());
    }

    private static final class StubDeliveryClient implements AiReportEmailDeliveryClient {
        private Long userId;
        private String yearMonth;

        @Override
        public AiReportEmailDeliverySummary deliver(Long userId, String yearMonth) {
            this.userId = userId;
            this.yearMonth = yearMonth;
            return new AiReportEmailDeliverySummary(yearMonth, "EMAIL", "bi***@example.com");
        }
    }

    private static final class StubAiReportQueryClient implements AiReportQueryClient {
        private Long userId;
        private String yearMonth;
        private AiReportSummary detail;
        private List<AiReportSummary> list = List.of();

        @Override
        public AiReportSummary findByUserIdAndYearMonth(Long userId, String yearMonth) {
            this.userId = userId;
            this.yearMonth = yearMonth;
            return detail;
        }

        @Override
        public List<AiReportSummary> findAllByUserId(Long userId) {
            this.userId = userId;
            return list;
        }
    }
}
