package com.ntropy.ai.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.ai.service.DailyTransactionClassificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 새벽 2시에 아직 분석되지 않은 거래의
 * 소비 분류를 실행하는 Scheduler입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTransactionClassificationScheduler {

    private final DailyTransactionClassificationService
            classificationService;

    /**
     * 매일 새벽 2시, 한국 시간 기준으로 실행합니다.
     *
     * 기본 크론 표현식:
     * 초 분 시 일 월 요일
     * 0  0  2  *  *  ?
     */
    @Scheduled(
            cron = "${transaction-classification.scheduler.daily-cron:0 0 2 * * ?}",
            zone = "Asia/Seoul"
    )
    public void runDailyTransactionClassification() {
        log.info("[일간 소비 분류] 배치 시작");

        try {
            int processed = classificationService.run();

            log.info(
                    "[일간 소비 분류] 배치 완료. processed={}",
                    processed
            );
        } catch (Exception exception) {
            /*
             * DB 저장 실패 등으로 배치가 중단되어 TXN_ANALYSIS가
             * 생성되지 않은 거래는 다음 실행 때 다시 조회됩니다.
             */
            log.error(
                    "[일간 소비 분류] 배치 실패",
                    exception
            );
        }
    }
}