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
public class AllocationGoal {

    private Long allocationGoalId;
    private Long jobId;
    private String targetMonth;
    private Long recommendHour;
}
