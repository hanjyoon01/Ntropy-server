package com.ntropy.work.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.JobSchedule;

/**
 * 테스트용 인메모리 JobScheduleMapper 구현체. 여러 서비스 테스트에서 공용으로 사용한다.
 */
public class InMemoryJobScheduleMapper implements JobScheduleMapper {

    private final Map<Long, JobSchedule> store = new LinkedHashMap<>();
    private long sequence = 1;

    @Override
    public void insert(JobSchedule jobSchedule) {
        jobSchedule.setScheduleId(sequence++);
        store.put(jobSchedule.getScheduleId(), jobSchedule);
    }

    @Override
    public JobSchedule findById(Long scheduleId) {
        return store.get(scheduleId);
    }

    @Override
    public List<JobSchedule> findByJobId(Long jobId) {
        List<JobSchedule> result = new ArrayList<>();
        for (JobSchedule schedule : store.values()) {
            if (jobId.equals(schedule.getJobId())) {
                result.add(schedule);
            }
        }
        return result;
    }

    @Override
    public void update(JobSchedule jobSchedule) {
        store.put(jobSchedule.getScheduleId(), jobSchedule);
    }

    @Override
    public void deleteById(Long scheduleId) {
        store.remove(scheduleId);
    }

    @Override
    public void deleteByJobId(Long jobId) {
        store.values().removeIf(schedule -> jobId.equals(schedule.getJobId()));
    }
}
