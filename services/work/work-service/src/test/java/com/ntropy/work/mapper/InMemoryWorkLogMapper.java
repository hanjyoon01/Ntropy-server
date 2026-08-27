package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.WorkLog;

/**
 * 테스트용 인메모리 WorkLogMapper 구현체. 여러 서비스 테스트에서 공용으로 사용한다.
 */
public class InMemoryWorkLogMapper implements WorkLogMapper {

    private final Map<Long, WorkLog> store = new LinkedHashMap<>();
    private long sequence = 1;

    @Override
    public void insert(WorkLog workLog) {
        workLog.setLogId(sequence++);
        store.put(workLog.getLogId(), workLog);
    }

    @Override
    public WorkLog findById(Long logId) {
        return store.get(logId);
    }

    @Override
    public List<WorkLog> findByJobId(Long jobId) {
        List<WorkLog> result = new ArrayList<>();
        for (WorkLog workLog : store.values()) {
            if (jobId.equals(workLog.getJobId())) {
                result.add(workLog);
            }
        }
        return result;
    }

    @Override
    public List<WorkLog> findByUserIdAndWorkDate(Long userId, LocalDate workDate) {
        List<WorkLog> result = new ArrayList<>();
        for (WorkLog workLog : store.values()) {
            if (userId.equals(workLog.getUserId()) && workDate.equals(workLog.getWorkDate())) {
                result.add(workLog);
            }
        }
        return result;
    }

    @Override
    public List<WorkLog> findByUserIdAndDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        List<WorkLog> result = new ArrayList<>();
        for (WorkLog workLog : store.values()) {
            if (userId.equals(workLog.getUserId())
                    && !workLog.getWorkDate().isBefore(startDate)
                    && !workLog.getWorkDate().isAfter(endDate)) {
                result.add(workLog);
            }
        }
        return result;
    }

    @Override
    public List<WorkLog> findByUserIdInAndDateRange(List<Long> userIds, LocalDate startDate, LocalDate endDate) {
        List<WorkLog> result = new ArrayList<>();
        for (WorkLog workLog : store.values()) {
            if (userIds.contains(workLog.getUserId())
                    && !workLog.getWorkDate().isBefore(startDate)
                    && !workLog.getWorkDate().isAfter(endDate)) {
                result.add(workLog);
            }
        }
        return result;
    }

    @Override
    public void update(WorkLog workLog) {
        store.put(workLog.getLogId(), workLog);
    }

    @Override
    public void deleteById(Long logId) {
        store.remove(logId);
    }
}
