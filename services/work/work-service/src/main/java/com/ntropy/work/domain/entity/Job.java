package com.ntropy.work.domain.entity;

import java.time.LocalDateTime;

import com.ntropy.work.domain.enums.SettlementType;

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
public class Job {

    private Long jobId;
    private Long userId;
    private Long categoryId;
    private String jobName;
    private SettlementType settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
    private Long monthlyExpectedIncome;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isActive;
}
