package com.ntropy.defense.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DefenseMode {
    private Long defenseId;
    private Long userId;
    private DefenseCause causeCode;
    private LocalDate unavailableStartDate;
    private LocalDate expectedReturnDate;
    private LocalDate returnDate;
    private Long reserveAmountSnapshot;
    private Long safeAssetAmountSnapshot;
    private Long availableAssetsSnapshot;
    private Long averageMonthlyExpense;
    private Long dailyExpense;
    private Integer dDay;
    private DefenseCalculationStatus calculationStatus;
    private DefenseModeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
