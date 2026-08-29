package com.ntropy.bff.dto.ai;

import com.ntropy.ai.api.dto.AiReportEmailDeliverySummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 리포트 이메일 전달 성공 응답. */
@Getter
@AllArgsConstructor
public class AiReportEmailDeliveryResponse {

    private final String yearMonth;
    private final String channel;
    private final String recipientEmail;

    public static AiReportEmailDeliveryResponse from(AiReportEmailDeliverySummary summary) {
        return new AiReportEmailDeliveryResponse(
                summary.yearMonth(),
                summary.channel(),
                summary.recipientEmail()
        );
    }
}
