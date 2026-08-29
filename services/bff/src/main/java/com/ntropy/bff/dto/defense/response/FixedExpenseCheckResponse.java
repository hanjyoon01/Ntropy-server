package com.ntropy.bff.dto.defense.response;

import com.ntropy.defense.api.dto.summary.FixedExpenseCheckSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class FixedExpenseCheckResponse {
    private Long totalExpectedAmount;
    private List<FixedExpenseResponse> expenses;

    public static FixedExpenseCheckResponse from(FixedExpenseCheckSummary summary) {
        if (summary == null) {
            return null;
        }
        return new FixedExpenseCheckResponse(
                summary.getTotalExpectedAmount(),
                summary.getExpenses().stream().map(FixedExpenseResponse::from).collect(Collectors.toList()));
    }
}
