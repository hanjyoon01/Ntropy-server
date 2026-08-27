package com.ntropy.account.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ntropy.account.config.DailyFinancialSyncSchedulingConfig;
import com.ntropy.account.service.DailyFinancialSyncOrchestrationService;

class DailyFinancialSyncSchedulerTest {

    @Test
    void runsEveryDayAtOneInSeoul() throws NoSuchMethodException {
        Scheduled schedule = DailyFinancialSyncScheduler.class
                .getMethod("runDailyFinancialSyncBatch")
                .getAnnotation(Scheduled.class);

        assertTrue(schedule.cron().contains("daily-cron:0 0 1 * * ?"));
        assertEquals("Asia/Seoul", schedule.zone());
        assertTrue(DailyFinancialSyncSchedulingConfig.class.isAnnotationPresent(EnableScheduling.class));
    }

    @Test
    void delegatesToOrchestrationService() {
        RecordingOrchestrationService orchestrationService = new RecordingOrchestrationService(false);
        DailyFinancialSyncScheduler scheduler = new DailyFinancialSyncScheduler(orchestrationService);

        scheduler.runDailyFinancialSyncBatch();

        assertEquals(1, orchestrationService.calls);
    }

    @Test
    void doesNotPropagateUnexpectedFailureToSchedulerThread() {
        RecordingOrchestrationService orchestrationService = new RecordingOrchestrationService(true);
        DailyFinancialSyncScheduler scheduler = new DailyFinancialSyncScheduler(orchestrationService);

        assertDoesNotThrow(scheduler::runDailyFinancialSyncBatch);
        assertEquals(1, orchestrationService.calls);
    }

    private static final class RecordingOrchestrationService extends DailyFinancialSyncOrchestrationService {

        private final boolean fail;
        private int calls;

        private RecordingOrchestrationService(boolean fail) {
            super(null, null, null, null);
            this.fail = fail;
        }

        @Override
        public void runPreviousDayBatch() {
            calls++;
            if (fail) {
                throw new IllegalStateException("unexpected failure");
            }
        }
    }
}
