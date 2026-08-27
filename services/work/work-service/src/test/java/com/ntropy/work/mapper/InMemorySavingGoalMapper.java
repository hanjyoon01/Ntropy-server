package com.ntropy.work.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ntropy.work.domain.entity.SavingGoal;

/**
 * 테스트용 인메모리 SavingGoalMapper 구현체.
 */
public class InMemorySavingGoalMapper implements SavingGoalMapper {

    private final Map<Long, SavingGoal> store = new LinkedHashMap<>();
    private long sequence = 1;

    @Override
    public void insert(SavingGoal savingGoal) {
        savingGoal.setSavingGoalId(sequence++);
        store.put(savingGoal.getSavingGoalId(), savingGoal);
    }

    @Override
    public SavingGoal findById(Long savingGoalId) {
        return store.get(savingGoalId);
    }

    @Override
    public SavingGoal findByUserIdAndTargetMonth(Long userId, String targetMonth) {
        for (SavingGoal savingGoal : store.values()) {
            if (savingGoal.getUserId().equals(userId) && savingGoal.getTargetMonth().equals(targetMonth)) {
                return savingGoal;
            }
        }
        return null;
    }

    @Override
    public void update(SavingGoal savingGoal) {
        store.put(savingGoal.getSavingGoalId(), savingGoal);
    }
}
