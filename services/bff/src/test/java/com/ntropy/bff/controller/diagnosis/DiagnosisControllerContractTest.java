package com.ntropy.bff.controller.diagnosis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.exception.GlobalExceptionHandler;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.api.client.DiagnosisResultQueryClient;
import com.ntropy.diagnosis.api.dto.DiagnosisResultSummary;

class DiagnosisControllerContractTest {

    private StubDiagnosisResultQueryClient queryClient;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryClient = new StubDiagnosisResultQueryClient();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        DiagnosisController controller = new DiagnosisController(
                queryClient,
                new AuthenticatedUserIdResolver()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void exposesMonthlyDiagnosisPathWithYearMonthAsPathVariable() throws Exception {
        Method method = DiagnosisController.class.getDeclaredMethod(
                "getDiagnosis", Authentication.class, String.class
        );

        assertArrayEquals(new String[] {"/{yearMonth}"}, method.getAnnotation(GetMapping.class).value());
    }

    @Test
    void returnsDiagnosisForAuthenticatedUserWithoutExposingInternalFields() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                42L,
                "unused",
                Collections.emptyList()
        );

        MvcResult result = mockMvc.perform(
                        get("/api/diagnosis/2026-07").principal(authentication)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = body.path("data");

        assertEquals(42L, queryClient.requestedUserId);
        assertEquals("2026-07", queryClient.requestedYearMonth);
        assertEquals(200, body.path("status_code").asInt());
        assertEquals(1L, data.path("diagnosisId").asLong());
        assertEquals("2026-07", data.path("yearMonth").asText());
        assertEquals("2026-07-05T09:30:00", data.path("calculatedAt").asText());
        assertFalse(data.has("userId"));
        assertFalse(data.has("createdAt"));
        assertFalse(data.has("updatedAt"));
    }

    @Test
    void returnsBadRequestForInvalidYearMonth() throws Exception {
        queryClient.error = ErrorCode.BAD_REQUEST;

        mockMvc.perform(get("/api/diagnosis/2026-13").principal(authentication()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundWhenDiagnosisDoesNotExist() throws Exception {
        queryClient.error = ErrorCode.NOT_FOUND;

        mockMvc.perform(get("/api/diagnosis/2026-06").principal(authentication()))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotAcceptUserIdAsRequestParameter() {
        for (Method method : DiagnosisController.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    assertFalse("userId".equals(requestParam.name()) || "userId".equals(requestParam.value()));
                }
            }
        }
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(42L, "unused", Collections.emptyList());
    }

    private static class StubDiagnosisResultQueryClient implements DiagnosisResultQueryClient {

        private Long requestedUserId;
        private String requestedYearMonth;
        private ErrorCode error;

        @Override
        public DiagnosisResultSummary findByUserIdAndYearMonth(Long userId, String yearMonth) {
            requestedUserId = userId;
            requestedYearMonth = yearMonth;
            if (error != null) {
                throw new ServiceException(error);
            }
            return new DiagnosisResultSummary(
                    1L,
                    userId,
                    yearMonth,
                    3_000_000L,
                    100_000L,
                    2_000_000L,
                    1_000_000L,
                    600_000L,
                    BigDecimal.valueOf(0.2),
                    5_000_000L,
                    3_000_000L,
                    2_000_000L,
                    LocalDateTime.of(2026, 7, 5, 9, 30)
            );
        }
    }
}
