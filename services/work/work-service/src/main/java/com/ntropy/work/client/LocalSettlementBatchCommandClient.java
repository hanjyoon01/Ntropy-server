package com.ntropy.work.client;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.SettlementBatchCommandClient;
import com.ntropy.work.service.SettlementService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalSettlementBatchCommandClient implements SettlementBatchCommandClient {

    private final SettlementService settlementService;

    @Override
    public boolean runForDate(Long userId, LocalDate processDate) {
        SettlementService.SettlementBatchOutcome outcome =
                settlementService.processSettlementDetailed(userId, processDate);
        if (outcome.createdCount() > 0) {
            settlementService.notifySettlementCompleted(
                    userId, processDate, outcome.createdCount(), outcome.totalAmount());
        }
        return outcome.createdCount() > 0;
    }
}
