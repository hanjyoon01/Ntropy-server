package com.ntropy.work.mapper;

import java.util.*;

import com.ntropy.work.domain.entity.AllocationGoal;

/**
 * 테스트용 인메모리 AllocationGoalMapper 구현체.
 */
public class InMemoryAllocationGoalMapper implements AllocationGoalMapper {

    private final Map<Long, AllocationGoal> store = new LinkedHashMap<>();
    private long sequence = 1;
    /**
     * 근무 시간 추천 테스트에서 사용할 잡 소유자 정보입니다.
     *
     * key: jobId
     * value: userId
     */
    private final Map<Long, Long> jobOwners =
            new HashMap<>();
    /**
     * 근무 시간 추천 테스트에서 잡과 사용자의 관계를 등록합니다.
     *
     * 실제 DB에서는 JOB.user_id를 조회하지만,
     * 인메모리 테스트에서는 이 메서드로 관계를 직접 등록합니다.
     */
    public void registerJobOwner(Long jobId, Long userId) {
        jobOwners.put(jobId, userId);
    }


    @Override
    public void insert(AllocationGoal allocationGoal) {
        allocationGoal.setAllocationGoalId(sequence++);
        store.put(allocationGoal.getAllocationGoalId(), allocationGoal);
    }

    @Override
    public AllocationGoal findById(Long allocationGoalId) {
        return store.get(allocationGoalId);
    }

    @Override
    public List<AllocationGoal> findByJobId(Long jobId) {
        List<AllocationGoal> result = new ArrayList<>();
        for (AllocationGoal goal : store.values()) {
            if (jobId.equals(goal.getJobId())) {
                result.add(goal);
            }
        }
        return result;
    }

    @Override
    public List<AllocationGoal> findByJobIdsAndTargetMonth(List<Long> jobIds, String targetMonth) {
        List<AllocationGoal> result = new ArrayList<>();
        for (AllocationGoal goal : store.values()) {
            if (jobIds.contains(goal.getJobId()) && targetMonth.equals(goal.getTargetMonth())) {
                result.add(goal);
            }
        }
        return result;
    }

    @Override
    public void update(AllocationGoal allocationGoal) {
        store.put(allocationGoal.getAllocationGoalId(), allocationGoal);
    }

    @Override
    public void deleteById(Long allocationGoalId) {
        store.remove(allocationGoalId);
    }

    @Override
    public void deleteByUserIdAndTargetMonth(
            Long userId,
            String targetMonth
    ) {
        /*
         * 실제 SQL의 의미:
         *
         * DELETE ag
         * FROM ALLOCATION_GOAL ag
         * JOIN JOB j ON j.job_id = ag.job_id
         * WHERE j.user_id = #{userId}
         * AND ag.target_month = #{targetMonth}
         */
        List<Long> deleteIds = store.values()
                .stream()
                .filter(goal ->
                        userId.equals(jobOwners.get(goal.getJobId())))
                .filter(goal ->
                        targetMonth.equals(goal.getTargetMonth()))
                .map(AllocationGoal::getAllocationGoalId)
                .toList();

        deleteIds.forEach(store::remove);
    }

    @Override
    public List<AllocationGoal> findByUserIdAndTargetMonth(
            Long userId,
            String targetMonth
    ) {
        /*
         * 실제 SQL 조회와 동일하게
         * userId와 targetMonth가 모두 일치하는 결과만 반환합니다.
         */
        return store.values()
                .stream()
                .filter(goal ->
                        userId.equals(jobOwners.get(goal.getJobId())))
                .filter(goal ->
                        targetMonth.equals(goal.getTargetMonth()))
                .toList();
    }
}
