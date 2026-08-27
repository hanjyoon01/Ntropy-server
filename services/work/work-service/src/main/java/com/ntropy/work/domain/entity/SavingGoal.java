package com.ntropy.work.domain.entity;

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
public class SavingGoal {

    private Long savingGoalId;
    private Long userId;
    private String targetMonth;
    private Long targetAmount;
    private Long laborIntensity;
}
