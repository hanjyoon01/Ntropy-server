package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ntropy.work.config.WorkReminderBatchUserScopeProperties;
import com.ntropy.work.domain.WorkLogStatus;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.port.notification.NotificationPort;
import com.ntropy.work.port.notification.NotificationRequest;
import com.ntropy.work.port.user.UserPort;

import lombok.RequiredArgsConstructor;

/**
 * 근무일지 관련 리마인더를 위해 활성 유저를 폴링해서 알림을 만드는 서비스.
 * WorkLog는 work-service가 소유한 데이터이므로, 상태(미기록/미확정) 판단도 work-service가
 * 직접 하고 NotificationPort로 알림 생성만 요청한다 - 원래 notification-service의
 * NotificationTriggerService에 있던 로직을 이 도메인으로 옮긴 것이다.
 * 스케줄러(WorkLogReminderScheduler)가 주기적으로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class WorkLogReminderService {

    private static final String STATUS_PLANNED = WorkLogStatus.PLANNED;
    private static final int UNCONFIRMED_REMINDER_DELAY_MINUTES = 60;
    private static final DateTimeFormatter UNCONFIRMED_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yy.M.d", Locale.KOREA);

    private final UserPort userPort;
    private final WorkReminderBatchUserScopeProperties userScopeProperties;
    private final WorkLogMapper workLogMapper;
    private final JobMapper jobMapper;
    private final NotificationPort notificationPort;

    /** 매일 지정된 시각에 실행. 오늘 근무 기록이 없는 유저에게 리마인더를 보낸다. */
    public void checkNoWorkLogToday() {
        LocalDate today = LocalDate.now();
        for (Long userId : userPort.findActiveUserIds(userScopeProperties.getUserScope())) {
            List<WorkLog> logs = workLogMapper.findByUserIdAndWorkDate(userId, today);
            if (logs.isEmpty()) {
                notificationPort.notify(new NotificationRequest(
                        userId,
                        "worklog-noWork-" + userId + "-" + today,
                        "WORK",
                        "오늘 근무 기록이 없어요",
                        "오늘 일하셨다면 근무일지를 작성해 주세요."
                ));
            }
        }
    }

    /**
     * 주기적으로 실행. 종료 후 UNCONFIRMED_REMINDER_DELAY_MINUTES가 지났는데 아직 확정 안 된
     * 근무일지가 있으면 건별로 리마인더를 보낸다. eventId를 근무일지 단위(logId)로 잡아서,
     * 폴링이 여러 번 겹쳐도 같은 근무일지에는 한 번만 알림이 간다.
     */
    public void checkUnconfirmedWorkLogs() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : userPort.findActiveUserIds(userScopeProperties.getUserScope())) {
            // 자정을 넘기는 근무는 work_date가 시작일 기준으로 저장되므로 어제 날짜도 함께 조회한다.
            for (WorkLog workLog : workLogMapper.findByUserIdAndDateRange(userId, today.minusDays(1), today)) {
                if (!STATUS_PLANNED.equals(workLog.getStatus())) {
                    continue;
                }
                LocalTime startTime = workLog.getStartTime();
                LocalTime endTime = workLog.getEndTime();
                if (endTime == null) {
                    continue;
                }
                LocalDateTime endDateTime = LocalDateTime.of(workLog.getWorkDate(), endTime);
                if (startTime != null && !endTime.isAfter(startTime)) {
                    endDateTime = endDateTime.plusDays(1); // 자정을 넘기는 근무 보정 (WorkTimeUtils와 동일 규칙)
                }
                LocalDateTime reminderAt = endDateTime.plusMinutes(UNCONFIRMED_REMINDER_DELAY_MINUTES);
                if (now.isBefore(reminderAt)) {
                    continue;
                }
                notificationPort.notify(new NotificationRequest(
                        userId,
                        "worklog-unconfirmed-" + workLog.getLogId(),
                        "WORK",
                        "확정되지 않은 근무가 있어요",
                        buildUnconfirmedBody(workLog)
                ));
            }
        }
    }

    /** "종료되었는데 확정되지 않은 근무가 있어요. (26.8.12 <근무 이름>)" 형태로 날짜/근무명을 붙인다.
     *  근무(Job)가 삭제되는 등으로 조회가 안 되면 근무명 없이 날짜만 표기한다. */
    private String buildUnconfirmedBody(WorkLog workLog) {
        String dateText = workLog.getWorkDate().format(UNCONFIRMED_DATE_FORMATTER);
        Job job = jobMapper.findById(workLog.getJobId());
        String jobName = job != null ? job.getJobName() : null;
        String suffix = jobName != null ? dateText + " " + jobName : dateText;
        return "종료되었는데 확정되지 않은 근무가 있어요. (" + suffix + ")";
    }
}
