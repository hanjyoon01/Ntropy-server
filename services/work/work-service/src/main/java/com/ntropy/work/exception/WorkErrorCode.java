package com.ntropy.work.exception;

import com.ntropy.common.exception.ServiceErrorCode;
import lombok.Getter;

@Getter
public enum WorkErrorCode implements ServiceErrorCode {
    JOB_NAME_REQUIRED(400, "job_name은 필수입니다."),
    CATEGORY_ID_REQUIRED(400, "category_id는 필수입니다."),
    SETTLEMENT_TYPE_REQUIRED(400, "settlement_type은 필수입니다."),
    HOURLY_WAGE_REQUIRED(400, "HOURLY 정산 방식은 hourly_wage가 필수입니다."),
    PER_TASK_FIELDS_REQUIRED(400, "PER_TASK 정산 방식은 per_task_wage와 task_per_hour가 모두 필수입니다."),
    MONTHLY_WAGE_REQUIRED(400, "MONTHLY 정산 방식은 monthly_wage가 필수입니다."),
    IS_REGULAR_REQUIRED(400, "is_regular는 필수입니다."),
    BASE_FATIGUE_REQUIRED(400, "base_fatigue는 필수입니다."),
    REGULAR_JOB_SCHEDULE_REQUIRED(400, "정기잡(is_regular=true)은 정기근무 스케줄이 최소 1개 필요합니다."),
    NON_REGULAR_JOB_SCHEDULE_NOT_ALLOWED(400, "비정기잡(is_regular=false)에는 정기근무 스케줄을 등록할 수 없습니다."),
    SCHEDULE_OVERLAP(409, "겹치는 정기근무 스케줄이 있습니다."),
    JOB_NOT_FOUND(404, "존재하지 않는 잡입니다."),
    JOB_ACCESS_DENIED(403, "본인 소유가 아닌 잡입니다."),

    WORK_LOG_USER_ID_REQUIRED(400, "user_id는 필수입니다."),
    WORK_LOG_JOB_ID_REQUIRED(400, "job_id는 필수입니다."),
    WORK_DATE_REQUIRED(400, "work_date는 필수입니다."),
    WORK_TIME_REQUIRED(400, "start_time/end_time은 필수입니다."),
    FATIGUE_REQUIRED_FOR_ACTUAL(400, "계획 외 등록은 fatigue가 필수입니다."),
    WORK_LOG_TIME_OVERLAP(409, "해당 시간대에 이미 등록된 근무일지가 있습니다."),
    TASK_COUNT_REQUIRED(400, "건별 정산 잡은 확정 시 task_count가 필요합니다."),
    WORK_LOG_NOT_FOUND(404, "존재하지 않는 근무일지입니다."),
    WORK_LOG_ACCESS_DENIED(403, "본인 소유가 아닌 근무일지입니다."),
    INVALID_WORK_TIME_RANGE(400, "시작 시간과 종료 시간이 같을 수 없습니다."),
    WORK_LOG_ALREADY_CONFIRMED(409, "이미 확정된 근무일지입니다."),
    WORK_LOG_SETTLEMENT_IN_PROGRESS(409, "정산이 진행 중이거나 완료된 근무일지는 수정할 수 없습니다."),

    CATEGORY_NOT_FOUND(404, "존재하지 않는 카테고리입니다."),
    PLATFORM_NOT_FOUND(404, "존재하지 않는 플랫폼입니다."),

    SAVING_GOAL_INVALID_TARGET_AMOUNT(400, "target_amount는 0보다 커야 합니다."),
    SAVING_GOAL_INVALID_LABOR_INTENSITY(400, "labor_intensity는 1~5 사이여야 합니다."),
    SAVING_GOAL_ALREADY_EXISTS(409, "이미 등록된 저축 목표입니다."),
    SAVING_GOAL_NOT_FOUND(404, "이번 달에 등록된 저축 목표가 없습니다."),

    JOB_PLATFORM_MAPPING_ALREADY_EXISTS(409, "이미 등록된 잡-플랫폼 매핑입니다.");

    private final int statusCode;
    private final String message;

    WorkErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
