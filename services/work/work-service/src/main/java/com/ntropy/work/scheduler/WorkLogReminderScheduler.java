package com.ntropy.work.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ntropy.work.service.WorkLogReminderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 근무일지 리마인더(미기록/미확정)를 확인하는 스케줄러.
 * 미기록 확인은 매일 정해진 시각 1회, 미확정 확인은 종료 후 1시간 시점을 놓치지 않도록
 * 짧은 간격으로 폴링한다. 원래 notification-service의 NotificationTriggerScheduler에
 * 있던 근무일지 관련 트리거를 work-service로 옮긴 것이다 - WorkLog 상태 판단은
 * work-service가 직접 하는 게 맞다고 판단.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkLogReminderScheduler {

    private final WorkLogReminderService workLogReminderService;

    @Scheduled(cron = "${worklog.reminder.no-work-log-cron:0 30 22 * * ?}", zone = "Asia/Seoul")
    public void checkNoWorkLogToday() {
        runSafely("근무일지 미작성 확인", workLogReminderService::checkNoWorkLogToday);
    }

    @Scheduled(cron = "${worklog.reminder.unconfirmed-cron:0 */3 * * * ?}", zone = "Asia/Seoul")
    public void checkUnconfirmedWorkLogs() {
        runSafely("근무일지 미확정 확인", workLogReminderService::checkUnconfirmedWorkLogs);
    }

    private void runSafely(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[근무일지 리마인더] {} 중 예상하지 못한 오류 발생", label, e);
        }
    }
}
