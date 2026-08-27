package com.ntropy.ai.scheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class TransactionClassificationSchedulerContractTest {

    @Test
    void dailyClassificationRunsAtTwoAndMonthlyReportRunsAtThree()
            throws NoSuchMethodException {

        Scheduled dailySchedule =
                DailyTransactionClassificationScheduler.class
                        .getMethod(
                                "runDailyTransactionClassification"
                        )
                        .getAnnotation(Scheduled.class);

        Scheduled monthlySchedule =
                MonthlyAiReportScheduler.class
                        .getMethod(
                                "runMonthlyAiReportBatch"
                        )
                        .getAnnotation(Scheduled.class);

        assertTrue(
                dailySchedule.cron().contains(
                        "daily-cron:0 0 2 * * ?"
                )
        );

        assertTrue(
                dailySchedule.zone().contains(
                        "Asia/Seoul"
                )
        );

        assertTrue(
                monthlySchedule.cron().contains(
                        "monthly-cron:0 0 3 1 * ?"
                )
        );

        assertTrue(
                monthlySchedule.zone().contains(
                        "Asia/Seoul"
                )
        );
    }
}