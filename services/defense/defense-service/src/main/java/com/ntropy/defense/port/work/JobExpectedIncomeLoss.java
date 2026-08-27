package com.ntropy.defense.port.work;

/**
 * defense-service가 방어모드 기간 중 잡별 예상 손실소득을 표현하는 값 타입.
 * work-service의 JobExpectedIncomeLossSummary와 필드 구성은 같지만, defense가 소유한
 * 별개의 타입이다.
 */
public record JobExpectedIncomeLoss(
        Long jobId,
        String jobName,
        Long expectedIncomeLoss
) {
}
