package com.ntropy.ai.port.work;

/**
 * ai-service가 월간 리포트에 사용하는 잡별 피로도 집계. work-service의 JobFatigueSummary와
 * 필드 구성은 같지만, ai가 소유한 별개의 타입이다.
 */
public record JobFatigue(
        Long jobId,
        String jobName,
        Integer workDays,
        Long totalWorkMinutes,
        Double averageFatigue,
        Long latestFatigue
) {
}
