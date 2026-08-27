package com.ntropy.ai.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.ai.service.MonthlyAiReportOrchestrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매월 AI 리포트 배치를 실행하는 Scheduler입니다.
 *
 * 실제 처리 과정은 MonthlyAiReportOrchestrationService가 담당하고,
 * 이 클래스는 정해진 시각에 배치를 시작합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyAiReportScheduler {

    private final MonthlyAiReportOrchestrationService
            orchestrationService;

    /**
     * 매월 1일 새벽 3시, 한국 시간 기준으로 실행합니다.
     *
     * 매일 새벽 2시에 실행되는 일간 소비 분류 배치 이후
     * 전월 TXN_ANALYSIS 데이터를 이용해 AI 리포트를 생성합니다.
     *
     * 기본 크론 표현식:
     * 초 분 시 일 월 요일
     * 0  0  3  1  *  ?
     */
    @Scheduled(
            cron = "${ai-report.scheduler.monthly-cron:0 0 3 1 * ?}",
            zone = "Asia/Seoul"
    )
    public void runMonthlyAiReportBatch() {
        log.info("[AI 리포트 배치] 월간 배치 스케줄 실행 시작");

        try {
            orchestrationService.runLastMonthBatch();

            log.info("[AI 리포트 배치] 월간 배치 스케줄 실행 완료");
        } catch (Exception exception) {
            /*
             * 예상하지 못한 예외가 발생해도 Scheduler 스레드가
             * 종료되지 않도록 오류를 기록합니다.
             */
            log.error(
                    "[AI 리포트 배치] 월간 배치 스케줄 실행 중 "
                            + "예상하지 못한 오류 발생",
                    exception
            );
        }
    }
}