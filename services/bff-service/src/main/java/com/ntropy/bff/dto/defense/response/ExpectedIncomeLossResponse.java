package com.ntropy.bff.dto.defense.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.defense.api.dto.summary.ExpectedIncomeLossSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class ExpectedIncomeLossResponse {
    private Long totalAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate periodStartDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate periodEndDate;
    private ExpectedIncomeLossCalculationStatus calculationStatus;
    private List<JobExpectedIncomeLossResponse> jobs;

    public static ExpectedIncomeLossResponse from(ExpectedIncomeLossSummary summary) {
        if (summary == null) {
            return null;
        }
        return new ExpectedIncomeLossResponse(
                summary.getTotalAmount(), summary.getPeriodStartDate(), summary.getPeriodEndDate(),
                toCalculationStatus(summary.getCalculationStatus()),
                summary.getJobs().stream()
                        .map(JobExpectedIncomeLossResponse::from)
                        .collect(Collectors.toList()));
    }

    private static ExpectedIncomeLossCalculationStatus toCalculationStatus(String status) {
        return status == null ? null : ExpectedIncomeLossCalculationStatus.valueOf(status);
    }
}
