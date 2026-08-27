package com.ntropy.work.api.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이번 달 저축목표 수정 요청. targetMonth는 수정 대상이 아니라(항상 이번 달 고정) 포함하지 않음.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavingGoalUpdateCommand {

    private Long targetAmount;
    private Long laborIntensity;
}
