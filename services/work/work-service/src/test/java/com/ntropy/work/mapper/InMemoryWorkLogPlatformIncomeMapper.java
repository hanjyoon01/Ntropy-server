package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.mapper.projection.VirtualSettlementIncome;

/**
 * 테스트용 인메모리 WorkLogPlatformIncomeMapper 구현체.
 * WORK_LOG 조인 쿼리를 흉내내기 위해 InMemoryWorkLogMapper를 함께 받는다.
 */
public class InMemoryWorkLogPlatformIncomeMapper implements WorkLogPlatformIncomeMapper {

    private final Map<Long, WorkLogPlatformIncome> store = new java.util.LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final InMemoryWorkLogMapper workLogMapper;

    public InMemoryWorkLogPlatformIncomeMapper(InMemoryWorkLogMapper workLogMapper) {
        this.workLogMapper = workLogMapper;
    }

    @Override
    public void insert(WorkLogPlatformIncome income) {
        income.setIncomeId(sequence.getAndIncrement());
        store.put(income.getIncomeId(), income);
    }

    @Override
    public void update(WorkLogPlatformIncome income) {
        store.put(income.getIncomeId(), income);
    }

    @Override
    public List<WorkLogPlatformIncome> findByLogId(Long logId) {
        List<WorkLogPlatformIncome> result = new ArrayList<>();
        for (WorkLogPlatformIncome income : store.values()) {
            if (income.getLogId().equals(logId)) {
                result.add(income);
            }
        }
        return result;
    }

    @Override
    public void deleteByLogId(Long logId) {
        store.values().removeIf(income -> income.getLogId().equals(logId));
    }

    @Override
    public List<WorkLogPlatformIncome> findConfirmedByJobIdAndPlatformIdAndDateRange(
            Long jobId, Long platformId, LocalDate startDate, LocalDate endDate) {
        List<WorkLogPlatformIncome> result = new ArrayList<>();
        for (WorkLogPlatformIncome income : store.values()) {
            if (!income.getPlatformId().equals(platformId)) {
                continue;
            }
            WorkLog workLog = workLogMapper.findById(income.getLogId());
            if (workLog == null || !jobId.equals(workLog.getJobId())) {
                continue;
            }
            if (!"CONFIRMED".equals(workLog.getStatus()) || workLog.getWorkDate() == null) {
                continue;
            }
            if (workLog.getWorkDate().isBefore(startDate) || workLog.getWorkDate().isAfter(endDate)) {
                continue;
            }
            result.add(income);
        }
        return result;
    }

    @Override
    public List<WorkLogPlatformIncome> findConfirmedByUserIdAndDateRange(
            Long userId, LocalDate startDate, LocalDate endDate) {
        List<WorkLogPlatformIncome> result = new ArrayList<>();
        for (WorkLogPlatformIncome income : store.values()) {
            WorkLog workLog = workLogMapper.findById(income.getLogId());
            if (workLog == null || !userId.equals(workLog.getUserId())) {
                continue;
            }
            if (!"CONFIRMED".equals(workLog.getStatus()) || workLog.getWorkDate() == null) {
                continue;
            }
            if (workLog.getWorkDate().isBefore(startDate) || workLog.getWorkDate().isAfter(endDate)) {
                continue;
            }
            result.add(income);
        }
        return result;
    }

    @Override
    public List<WorkLogPlatformIncome> findConfirmedByUserIdInAndDateRange(
            List<Long> userIds, LocalDate startDate, LocalDate endDate) {
        List<WorkLogPlatformIncome> result = new ArrayList<>();
        for (WorkLogPlatformIncome income : store.values()) {
            WorkLog workLog = workLogMapper.findById(income.getLogId());
            if (workLog == null || !userIds.contains(workLog.getUserId())) {
                continue;
            }
            if (!"CONFIRMED".equals(workLog.getStatus()) || workLog.getWorkDate() == null) {
                continue;
            }
            if (workLog.getWorkDate().isBefore(startDate) || workLog.getWorkDate().isAfter(endDate)) {
                continue;
            }
            result.add(income);
        }
        return result;
    }

    @Override
    public List<VirtualSettlementIncome> findConfirmedByUserIdUpToDateForVirtualSettlement(
            Long userId, LocalDate endDate) {
        List<VirtualSettlementIncome> result = new ArrayList<>();
        for (WorkLogPlatformIncome income : store.values()) {
            WorkLog workLog = workLogMapper.findById(income.getLogId());
            if (workLog == null || !userId.equals(workLog.getUserId())) {
                continue;
            }
            if (!"CONFIRMED".equals(workLog.getStatus())
                    || workLog.getWorkDate() == null
                    || workLog.getWorkDate().isAfter(endDate)) {
                continue;
            }
            VirtualSettlementIncome projection = new VirtualSettlementIncome();
            projection.setIncomeId(income.getIncomeId());
            projection.setUserId(workLog.getUserId());
            projection.setPlatformId(income.getPlatformId());
            projection.setWorkDate(workLog.getWorkDate());
            projection.setExpectedAmount(income.getExpectedAmount());
            projection.setSettlementStatus(income.getSettlementStatus());
            result.add(projection);
        }
        return result;
    }
}
