package com.ntropy.ai.port.work;

/**
 * ai-service가 월간 리포트에 사용하는 잡별 소득 비중. work-service의 JobIncomeSummary와
 * 필드 구성은 같지만, ai가 소유한 별개의 타입이다.
 */
public record JobIncome(
        Long jobId,
        String jobName,
        Long incomeAmount,
        Double incomeRatio
) {
}
