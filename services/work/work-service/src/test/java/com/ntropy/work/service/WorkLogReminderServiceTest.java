package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.domain.UserScope;
import com.ntropy.work.config.WorkReminderBatchUserScopeProperties;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;
import com.ntropy.work.port.notification.NotificationPort;
import com.ntropy.work.port.notification.NotificationRequest;
import com.ntropy.work.port.user.UserPort;

class WorkLogReminderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_ID = 100L;

    private final InMemoryWorkLogMapper workLogMapper = new InMemoryWorkLogMapper();
    private final InMemoryJobMapper jobMapper = new InMemoryJobMapper();
    private final StubUserPort userPort = new StubUserPort();
    private final StubNotificationPort notificationPort = new StubNotificationPort();
    private final WorkLogReminderService service =
            new WorkLogReminderService(
                    userPort,
                    new WorkReminderBatchUserScopeProperties("REAL_ONLY"),
                    workLogMapper,
                    jobMapper,
                    notificationPort);

    @BeforeEach
    void setUp() {
        userPort.userIds = List.of(USER_ID);
        jobMapper.seed(Job.builder().jobId(JOB_ID).userId(USER_ID).jobName("배달의민족").isActive(true).build());
    }

    @Test
    @DisplayName("오늘 근무일지가 하나도 없으면 미기록 리마인더를 보낸다")
    void checkNoWorkLogToday_noLogs_sendsNotification() {
        service.checkNoWorkLogToday();

        assertEquals(1, notificationPort.sent.size());
        assertEquals("WORK", notificationPort.sent.get(0).notificationType());
    }

    @Test
    @DisplayName("오늘 근무일지가 있으면 미기록 리마인더를 보내지 않는다")
    void checkNoWorkLogToday_hasLog_doesNotSendNotification() {
        workLogMapper.insert(workLog(LocalDate.now(), "CONFIRMED"));

        service.checkNoWorkLogToday();

        assertEquals(0, notificationPort.sent.size());
    }

    @Test
    @DisplayName("종료 후 1시간이 지난 PLANNED 근무일지가 있으면 건별로 미확정 리마인더를 보낸다")
    void checkUnconfirmedWorkLogs_pastDelay_sendsNotificationPerWorkLog() {
        WorkLog log = workLogEndingAt(LocalDateTime.now().minusHours(1).minusMinutes(1), "PLANNED");
        workLogMapper.insert(log);

        service.checkUnconfirmedWorkLogs();

        assertEquals(1, notificationPort.sent.size());
        assertEquals("worklog-unconfirmed-" + log.getLogId(), notificationPort.sent.get(0).eventId());
    }

    @Test
    @DisplayName("미확정 리마인더 본문에 날짜와 근무명이 담긴다")
    void checkUnconfirmedWorkLogs_pastDelay_bodyContainsDateAndJobName() {
        LocalDateTime endAt = LocalDateTime.now().minusHours(2);
        WorkLog log = WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(endAt.toLocalDate())
                .status("PLANNED")
                .startTime(endAt.minusHours(1).toLocalTime())
                .endTime(endAt.toLocalTime())
                .build();
        workLogMapper.insert(log);

        service.checkUnconfirmedWorkLogs();

        assertEquals(1, notificationPort.sent.size());
        String expectedDate = endAt.toLocalDate().format(DateTimeFormatter.ofPattern("yy.M.d", Locale.KOREA));
        assertEquals(
                "종료되었는데 확정되지 않은 근무가 있어요. (" + expectedDate + " 배달의민족)",
                notificationPort.sent.get(0).body());
    }

    @Test
    @DisplayName("근무(Job) 조회가 안 되면 근무명 없이 날짜만 본문에 담는다")
    void checkUnconfirmedWorkLogs_jobNotFound_bodyContainsDateOnly() {
        LocalDateTime endAt = LocalDateTime.now().minusHours(2);
        WorkLog log = WorkLog.builder()
                .userId(USER_ID)
                .jobId(999L) // 시딩되지 않은 jobId
                .workDate(endAt.toLocalDate())
                .status("PLANNED")
                .startTime(endAt.minusHours(1).toLocalTime())
                .endTime(endAt.toLocalTime())
                .build();
        workLogMapper.insert(log);

        service.checkUnconfirmedWorkLogs();

        assertEquals(1, notificationPort.sent.size());
        String expectedDate = endAt.toLocalDate().format(DateTimeFormatter.ofPattern("yy.M.d", Locale.KOREA));
        assertEquals(
                "종료되었는데 확정되지 않은 근무가 있어요. (" + expectedDate + ")",
                notificationPort.sent.get(0).body());
    }

    @Test
    @DisplayName("종료 후 1시간이 지나지 않았으면 미확정 리마인더를 보내지 않는다")
    void checkUnconfirmedWorkLogs_beforeDelayElapsed_doesNotSendNotification() {
        workLogMapper.insert(workLogEndingAt(LocalDateTime.now().minusMinutes(30), "PLANNED"));

        service.checkUnconfirmedWorkLogs();

        assertEquals(0, notificationPort.sent.size());
    }

    @Test
    @DisplayName("종료 후 1시간이 지났어도 CONFIRMED면 미확정 리마인더를 보내지 않는다")
    void checkUnconfirmedWorkLogs_confirmed_doesNotSendNotification() {
        workLogMapper.insert(workLogEndingAt(LocalDateTime.now().minusHours(2), "CONFIRMED"));

        service.checkUnconfirmedWorkLogs();

        assertEquals(0, notificationPort.sent.size());
    }

    @Test
    @DisplayName("종료 후 1시간이 지난 미확정 근무일지가 여러 건이면 각각 알림을 보낸다")
    void checkUnconfirmedWorkLogs_multiplePastDelay_sendsOnePerWorkLog() {
        workLogMapper.insert(workLogEndingAt(LocalDateTime.now().minusHours(2), "PLANNED"));
        workLogMapper.insert(workLogEndingAt(LocalDateTime.now().minusHours(3), "PLANNED"));

        service.checkUnconfirmedWorkLogs();

        assertEquals(2, notificationPort.sent.size());
    }

    @Test
    @DisplayName("오늘 근무일지가 아예 없으면 미확정 리마인더도 보내지 않는다")
    void checkUnconfirmedWorkLogs_noLogsAtAll_doesNotSendNotification() {
        service.checkUnconfirmedWorkLogs();

        assertEquals(0, notificationPort.sent.size());
    }

    @Test
    @DisplayName("설정된 batch.work-reminder.user-scope를 UserPort에 그대로 전달한다")
    void checkNoWorkLogToday_passesConfiguredUserScopeToClient() {
        service.checkNoWorkLogToday();

        assertEquals(UserScope.REAL_ONLY, userPort.lastRequestedScope);
    }

    private static WorkLog workLog(LocalDate workDate, String status) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(workDate)
                .status(status)
                .build();
    }

    /** endAt(날짜+시각)을 workDate/endTime으로 분리해 담는다 - 자정 경계에서도 날짜와 시각이 항상 같이 굴러가게 하기 위함. */
    private static WorkLog workLogEndingAt(LocalDateTime endAt, String status) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(endAt.toLocalDate())
                .status(status)
                .endTime(endAt.toLocalTime())
                .build();
    }

    private static final class StubUserPort implements UserPort {
        private List<Long> userIds = List.of();
        private UserScope lastRequestedScope;

        @Override
        public List<Long> findActiveUserIds(UserScope scope) {
            this.lastRequestedScope = scope;
            return userIds;
        }
    }

    private static final class StubNotificationPort implements NotificationPort {
        private final List<NotificationRequest> sent = new ArrayList<>();

        @Override
        public void notify(NotificationRequest request) {
            sent.add(request);
        }
    }
}
