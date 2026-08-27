package com.ntropy.account.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.account.service.DailyFinancialSyncOrchestrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 전날 금융거래 증분 동기화를 매일 실행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyFinancialSyncScheduler {

    private final DailyFinancialSyncOrchestrationService orchestrationService;

    /**
     * 기본적으로 서울 시간 매일 새벽 1시에 실행한다.
     * 새벽 2시 일간 소비 분류보다 먼저 전날 거래를 수집하도록 시각을 분리한다.
     */
    @Scheduled(
            cron = "${daily-financial-sync.scheduler.daily-cron:0 0 1 * * ?}",
            zone = "Asia/Seoul"
    )
    public void runDailyFinancialSyncBatch() {
        log.info("[일일 금융거래 동기화] 스케줄 실행 시작");
        try {
            orchestrationService.runPreviousDayBatch();
            log.info("[일일 금융거래 동기화] 스케줄 실행 완료");
        } catch (RuntimeException unexpectedFailure) {
            // 활성 사용자 조회 등 provider 실행 경계 밖의 예외가 스케줄러 스레드를 종료하지 않게 한다.
            log.error("[일일 금융거래 동기화] 스케줄 실행 중 예상하지 못한 오류 발생", unexpectedFailure);
        }
    }
}
