package com.ntropy.work.domain.enums;

/**
 * JOB의 정산 방식. work-service 내부 도메인 개념이라 common으로 노출하지 않는다.
 * common DTO(JobSummary 등)와 경계를 넘을 때는 String으로 변환한다
 * (LocalJobCommandClient/LocalJobQueryClient 참고).
 */
public enum SettlementType {
    HOURLY,
    PER_TASK,
    MONTHLY
}
