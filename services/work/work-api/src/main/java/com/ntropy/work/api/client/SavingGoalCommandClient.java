package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.command.SavingGoalRegisterCommand;
import com.ntropy.work.api.dto.command.SavingGoalUpdateCommand;

/**
 * work-service의 SAVING_GOAL 쓰기 계약. work-service가 LocalSavingGoalCommandClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface SavingGoalCommandClient {

    Long registerSavingGoal(SavingGoalRegisterCommand command);

    /** 이번 달(서버 시간 기준) 저축목표를 수정한다. 이번 달에 등록된 게 없으면 예외. */
    void updateSavingGoal(Long userId, SavingGoalUpdateCommand command);
}
