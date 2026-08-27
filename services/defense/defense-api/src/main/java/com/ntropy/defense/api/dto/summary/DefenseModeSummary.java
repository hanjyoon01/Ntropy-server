package com.ntropy.defense.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseModeSummary {
    private Long defenseId;
    private Long userId;
    private String causeCode;
    private String causeName;
    private LocalDate unavailableStartDate;
    private LocalDate expectedReturnDate;
    private LocalDate returnDate;
    private Long reserveAmountSnapshot;
    private Long safeAssetAmountSnapshot;
    private Long availableAssetsSnapshot;
    private Long averageMonthlyExpense;
    private Long dailyExpense;
    private Integer dDay;
    private String calculationStatus;
    private String status;
    private LocalDateTime createdAt;
    private List<DefenseChecklistSummary> checklist;
    private FixedExpenseCheckSummary fixedExpenseCheck;
    private ExpectedIncomeLossSummary expectedIncomeLoss;
    private GrowthModeSummary growthMode;
}
