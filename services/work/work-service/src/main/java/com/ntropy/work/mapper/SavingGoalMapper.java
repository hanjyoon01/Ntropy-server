package com.ntropy.work.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.SavingGoal;

@Mapper
public interface SavingGoalMapper {

    void insert(SavingGoal savingGoal);

    SavingGoal findById(Long savingGoalId);

    SavingGoal findByUserIdAndTargetMonth(@Param("userId") Long userId,
                                           @Param("targetMonth") String targetMonth);

    void update(SavingGoal savingGoal);
}
