package com.ntropy.work.api.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저축목표 등록 요청. savingGoalId는 work-service가 채우므로 여기 없음.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavingGoalRegisterCommand {

    private Long userId;
    private String targetMonth;
    private Long targetAmount;
    private Long laborIntensity;
}
