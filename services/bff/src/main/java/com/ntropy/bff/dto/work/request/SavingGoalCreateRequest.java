package com.ntropy.bff.dto.work.request;

import com.ntropy.work.api.dto.command.SavingGoalRegisterCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SavingGoalCreateRequest {

    private String targetMonth;
    private Long targetAmount;
    private Long laborIntensity;

    public SavingGoalRegisterCommand toCommand(Long userId) {
        return new SavingGoalRegisterCommand(userId, targetMonth, targetAmount, laborIntensity);
    }
}
