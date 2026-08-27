package com.ntropy.work.domain;

/**
 * WorkLog.status 값. WorkLogService, WorkLogReminderService, CalendarService 등
 * 여러 서비스에서 공통으로 참조하는 문자열 상수다.
 */
public final class WorkLogStatus {

    public static final String PLANNED = "PLANNED";
    public static final String CONFIRMED = "CONFIRMED";

    private WorkLogStatus() {
    }
}
