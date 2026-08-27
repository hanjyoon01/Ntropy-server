package com.ntropy.ai.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AiReportDetailSummaryTest {

    @Test
    void recursivelyConvertsTheSameStoredJsonUsedByApiAndPdf() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AiReportSummary source = new AiReportSummary(
                1L, 2L, "2026-05",
                mapper.readTree("{\"total_income\":100,\"job_summaries\":[{\"job_name\":\"배달\"}]}"),
                mapper.readTree("{\"recommended_product\":{\"details\":{\"minimum_spend\":300000}}}"),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        );

        AiReportDetailSummary detail = AiReportDetailSummary.from(source);

        assertEquals(100, detail.financialSummary().path("totalIncome").asInt());
        assertEquals("배달", detail.financialSummary().path("jobSummaries").get(0).path("jobName").asText());
        assertEquals(300000, detail.recommendation().path("recommendedProduct")
                .path("details").path("minimumSpend").asInt());
        assertTrue(detail.recommendation().path("recommended_product").isMissingNode());
    }
}
