package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.enums.SettlementMatchStatus;

/**
 * 테스트용 인메모리 SettlementMapper 구현체.
 */
public class InMemorySettlementMapper implements SettlementMapper {

    private final List<Settlement> store = new ArrayList<>();
    private long sequence = 1;

    @Override
    public void insert(Settlement settlement) {
        settlement.setSettlementId(sequence++);
        store.add(settlement);
    }

    @Override
    public boolean existsByAccountTransactionId(Long accountTransactionId) {
        return store.stream().anyMatch(settlement ->
                accountTransactionId != null && accountTransactionId.equals(settlement.getAccountTransactionId()));
    }

    @Override
    public boolean existsByUserIdAndStatusAndPeriod(Long userId, SettlementMatchStatus status,
                                                     LocalDate periodStart, LocalDate periodEnd) {
        return store.stream().anyMatch(settlement ->
                settlement.getUserId().equals(userId)
                        && settlement.getStatus() == status
                        && settlement.getPeriodStart().equals(periodStart)
                        && settlement.getPeriodEnd().equals(periodEnd));
    }

    @Override
    public List<Settlement> findByUserIdAndDepositDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : store) {
            if (settlement.getUserId().equals(userId)
                    && !settlement.getDepositDate().isBefore(startDate)
                    && !settlement.getDepositDate().isAfter(endDate)) {
                result.add(settlement);
            }
        }
        return result;
    }

    @Override
    public List<Settlement> findByUserIdInAndDepositDateRange(List<Long> userIds, LocalDate startDate,
                                                                LocalDate endDate) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : store) {
            if (userIds.contains(settlement.getUserId())
                    && !settlement.getDepositDate().isBefore(startDate)
                    && !settlement.getDepositDate().isAfter(endDate)) {
                result.add(settlement);
            }
        }
        return result;
    }

    @Override
    public List<Settlement> findByJobIdInAndDepositDateRangeAndStatus(List<Long> jobIds, LocalDate startDate,
                                                                       LocalDate endDate, SettlementMatchStatus status) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : store) {
            if (jobIds.contains(settlement.getJobId())
                    && !settlement.getDepositDate().isBefore(startDate)
                    && !settlement.getDepositDate().isAfter(endDate)
                    && settlement.getStatus() == status) {
                result.add(settlement);
            }
        }
        return result;
    }

    public List<Settlement> findAll() {
        return store;
    }
}
