package com.ntropy.bff.dto.work.response;

import com.ntropy.work.api.dto.summary.SavingGoalSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SavingGoalResponse {

    private Long savingGoalId;
    private String targetMonth;
    private Long targetAmount;
    private Long laborIntensity;

    /** 이번 달 등록된 저축목표가 없으면 null. */
    public static SavingGoalResponse from(SavingGoalSummary summary) {
        if (summary == null) {
            return null;
        }
        SavingGoalResponse response = new SavingGoalResponse();
        response.savingGoalId = summary.getSavingGoalId();
        response.targetMonth = summary.getTargetMonth();
        response.targetAmount = summary.getTargetAmount();
        response.laborIntensity = summary.getLaborIntensity();
        return response;
    }
}
