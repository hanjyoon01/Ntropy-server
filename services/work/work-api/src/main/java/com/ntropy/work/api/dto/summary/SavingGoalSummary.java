package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * work-service의 SAVING_GOAL을 다른 서비스/bff-service에 노출하기 위한 공유 DTO.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingGoalSummary {

    private Long savingGoalId;
    private String targetMonth;
    private Long targetAmount;
    private Long laborIntensity;
}
