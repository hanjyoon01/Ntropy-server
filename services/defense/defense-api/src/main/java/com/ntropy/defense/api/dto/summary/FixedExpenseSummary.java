package com.ntropy.defense.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FixedExpenseSummary {
    private Long commitmentId;
    private Long accountId;
    private String expenseType;
    private String expenseName;
    private String productName;
    private Long outstandingBalance;
    private Long expectedAmount;
    private LocalDate nextPaymentDate;
    private String amountStatus;
    private String dateStatus;
    private Integer dDayBefore;
    private Integer dDayAfter;
    private Integer dDayReduction;
    private FixedExpenseMaintainStatus maintainStatus;
}
