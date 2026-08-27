package com.ntropy.work.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.work.domain.VirtualSettlementDepositBatchResult;
import com.ntropy.work.service.SettlementService;
import com.ntropy.work.service.VirtualSettlementDepositService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 정산 배치를 실행하는 스케줄러입니다.
 *
 * 실제 처리는 SettlementService.runDailyBatch()에 맡기고,
 * 이 클래스는 정해진 시각에 배치를 시작하는 역할만 담당합니다.
 *
 * account-service의 Codef 계좌 동기화 배치가 매일 새벽 1시쯤 끝나는 것을 전제로,
 * 그 이후 시각(새벽 1시 30분)에 실행되도록 기본값을 잡았습니다. Codef 배치 완료 시각이
 * 바뀌면 settlement.scheduler.daily-cron 설정으로 조정하면 됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;
    private final VirtualSettlementDepositService virtualSettlementDepositService;

    /**
     * 매일 새벽 1시 30분, 한국 시간 기준으로 실행됩니다.
     *
     * 기본 크론 표현식:
     * 초 분 시 일 월 요일
     * 0  30 1  *  *  ?
     */
    @Scheduled(
            cron = "${settlement.scheduler.daily-cron:0 30 1 * * ?}",
            zone = "Asia/Seoul"
    )
    public void runDailySettlementBatch() {
        log.info("[정산 배치] 일일 정산 배치 스케줄 실행 시작 (실행시각: {})", LocalDateTime.now());

        LocalDate today = LocalDate.now();
        try {
            VirtualSettlementDepositBatchResult result = virtualSettlementDepositService.runDailyBatch(today);
            log.info("[정산 배치] 가상계좌 정산 입금 생성 완료. createdDeposits={}, matchTargets={}",
                    result.createdCount(), result.matchTargets().size());
            processVirtualSettlementTargets(result, today);
        } catch (Exception exception) {
            // 가상계좌 입금 생성 실패가 실제 CODEF 입금의 정산 매칭을 막아서는 안 된다.
            log.error("[정산 배치] 가상계좌 정산 입금 생성 중 오류 발생", exception);
        }

        try {
            settlementService.runDailyBatch();
            log.info("[정산 배치] 일일 정산 배치 스케줄 실행 완료 (실행시각: {})", LocalDateTime.now());
        } catch (Exception exception) {
            // 예상하지 못한 예외가 발생해도 스케줄러 스레드가 죽지 않도록 기록합니다.
            log.error("[정산 배치] 일일 정산 배치 스케줄 실행 중 예상하지 못한 오류 발생 (실행시각: {})",
                    LocalDateTime.now(), exception);
        }
    }

    /** 최근 3일 백필 범위 밖으로 생성된 과거 가상 입금도 같은 실행에서 즉시 매칭한다. */
    private void processVirtualSettlementTargets(VirtualSettlementDepositBatchResult result, LocalDate today) {
        Map<Long, SettlementService.SettlementBatchOutcome> outcomesByUser = new LinkedHashMap<>();
        for (VirtualSettlementDepositBatchResult.MatchTarget target : result.matchTargets()) {
            try {
                SettlementService.SettlementBatchOutcome outcome = settlementService.processSettlementDetailed(
                        target.userId(), target.depositDate());
                if (outcome.createdCount() > 0) {
                    outcomesByUser.merge(target.userId(), outcome, (left, right) ->
                            new SettlementService.SettlementBatchOutcome(
                                    left.createdCount() + right.createdCount(),
                                    Math.addExact(left.totalAmount(), right.totalAmount())
                            ));
                }
            } catch (RuntimeException e) {
                log.error("[정산 배치] 가상 입금 즉시 매칭 실패. userId={}, depositDate={}",
                        target.userId(), target.depositDate(), e);
            }
        }
        for (Map.Entry<Long, SettlementService.SettlementBatchOutcome> entry : outcomesByUser.entrySet()) {
            SettlementService.SettlementBatchOutcome outcome = entry.getValue();
            settlementService.notifySettlementCompleted(
                    entry.getKey(), today, outcome.createdCount(), outcome.totalAmount());
        }
    }
}
