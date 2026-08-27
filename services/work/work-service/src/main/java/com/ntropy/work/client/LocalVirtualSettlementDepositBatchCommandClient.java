package com.ntropy.work.client;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult;
import com.ntropy.work.service.SettlementService;
import com.ntropy.work.service.VirtualSettlementDepositService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 로컬 모듈 안에서 가상 입금 생성과 정산 매칭을 연속 실행하는 테스트용 어댑터. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalVirtualSettlementDepositBatchCommandClient
        implements VirtualSettlementDepositBatchCommandClient {

    private final VirtualSettlementDepositService virtualSettlementDepositService;
    private final SettlementService settlementService;

    @Override
    public BatchResult runForDate(Long userId, LocalDate processDate) {
        VirtualSettlementDepositBatchResult depositResult =
                virtualSettlementDepositService.processUser(userId, processDate);

        int matchedSettlementCount = 0;
        long matchedSettlementAmount = 0L;
        for (VirtualSettlementDepositBatchResult.MatchTarget target : depositResult.matchTargets()) {
            try {
                SettlementService.SettlementBatchOutcome outcome = settlementService.processSettlementDetailed(
                        target.userId(), target.depositDate());
                matchedSettlementCount = Math.addExact(matchedSettlementCount, outcome.createdCount());
                matchedSettlementAmount = Math.addExact(matchedSettlementAmount, outcome.totalAmount());
            } catch (RuntimeException e) {
                // 한 매칭 대상의 실패가 같은 호출의 다른 대상 매칭·응답까지 날려버리지 않게 격리한다.
                log.error("[가상 정산 입금 테스트] 정산 매칭 실패. userId={}, depositDate={}",
                        target.userId(), target.depositDate(), e);
            }
        }

        if (matchedSettlementCount > 0) {
            settlementService.notifySettlementCompleted(
                    userId, processDate, matchedSettlementCount, matchedSettlementAmount);
        }

        log.info("[가상 정산 입금 테스트] 수동 배치 완료. userId={}, processDate={}, "
                        + "createdDeposits={}, matchedSettlements={}",
                userId, processDate, depositResult.createdCount(), matchedSettlementCount);
        return new BatchResult(depositResult.createdCount(), matchedSettlementCount);
    }
}
