package com.ntropy.work.client;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.SavingGoalCommandClient;
import com.ntropy.work.api.dto.command.SavingGoalRegisterCommand;
import com.ntropy.work.api.dto.command.SavingGoalUpdateCommand;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.service.SavingGoalService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalSavingGoalCommandClient implements SavingGoalCommandClient {

    private final SavingGoalService savingGoalService;

    @Override
    public Long registerSavingGoal(SavingGoalRegisterCommand command) {
        SavingGoal savingGoal = SavingGoal.builder()
                .userId(command.getUserId())
                .targetMonth(command.getTargetMonth())
                .targetAmount(command.getTargetAmount())
                .laborIntensity(command.getLaborIntensity())
                .build();

        savingGoalService.registerSavingGoal(savingGoal);
        return savingGoal.getSavingGoalId();
    }

    @Override
    public void updateSavingGoal(Long userId, SavingGoalUpdateCommand command) {
        savingGoalService.updateCurrentMonthGoal(userId, command.getTargetAmount(), command.getLaborIntensity());
    }
}
