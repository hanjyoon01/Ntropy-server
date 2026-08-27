package com.ntropy.ai.api.dto;

/** 이메일 전달 성공 결과. 이메일 주소는 구현체가 마스킹한 값만 노출한다. */
public record AiReportEmailDeliverySummary(
        String yearMonth,
        String channel,
        String recipientEmail
) {
}
