package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.AllocationGoal;

@Mapper
public interface AllocationGoalMapper {

    void insert(AllocationGoal allocationGoal);

    AllocationGoal findById(Long allocationGoalId);

    List<AllocationGoal> findByJobId(Long jobId);

    List<AllocationGoal> findByJobIdsAndTargetMonth(@Param("jobIds") List<Long> jobIds,
                                                      @Param("targetMonth") String targetMonth);

    void update(AllocationGoal allocationGoal);

    void deleteById(Long allocationGoalId);

    List<AllocationGoal> findByUserIdAndTargetMonth(@Param("userId") Long userId,
                                                    @Param("targetMonth") String targetMonth);

    void deleteByUserIdAndTargetMonth(@Param("userId") Long userId,
                                      @Param("targetMonth") String targetMonth);
}
