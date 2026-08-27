package com.ntropy.work.domain.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.ntropy.work.domain.enums.SettlementStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkLog {

    private Long logId;
    private Long userId;
    private Long jobId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long taskCount;
    private Long fatigue;
    private Long estimatedIncome;
    private String status;
    private SettlementStatus settlementStatus;
}
