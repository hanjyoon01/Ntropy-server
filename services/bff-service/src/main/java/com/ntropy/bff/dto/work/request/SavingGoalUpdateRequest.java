package com.ntropy.bff.dto.work.request;

import com.ntropy.work.api.dto.command.SavingGoalUpdateCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SavingGoalUpdateRequest {

    private Long targetAmount;
    private Long laborIntensity;

    public SavingGoalUpdateCommand toCommand() {
        return new SavingGoalUpdateCommand(targetAmount, laborIntensity);
    }
}
