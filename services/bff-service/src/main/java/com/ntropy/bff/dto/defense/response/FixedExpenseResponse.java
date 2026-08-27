package com.ntropy.bff.dto.defense.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.defense.api.dto.summary.FixedExpenseSummary;
import com.ntropy.defense.api.dto.summary.FixedExpenseMaintainStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class FixedExpenseResponse {
    private Long commitmentId;
    private Long accountId;
    private String expenseType;
    private String expenseName;
    private String productName;
    private Long outstandingBalance;
    private Long expectedAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate nextPaymentDate;
    private String amountStatus;
    private String dateStatus;
    private Integer survivalDaysBefore;
    private Integer survivalDaysAfter;
    private Integer reducedDays;
    private FixedExpenseMaintainStatus maintainStatus;

    public static FixedExpenseResponse from(FixedExpenseSummary summary) {
        return new FixedExpenseResponse(
                summary.getCommitmentId(), summary.getAccountId(), summary.getExpenseType(),
                summary.getExpenseName(), summary.getProductName(), summary.getOutstandingBalance(),
                summary.getExpectedAmount(), summary.getNextPaymentDate(), summary.getAmountStatus(),
                summary.getDateStatus(), summary.getDDayBefore(), summary.getDDayAfter(),
                summary.getDDayReduction(), summary.getMaintainStatus());
    }
}
