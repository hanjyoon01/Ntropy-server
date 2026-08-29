package com.ntropy.bff.dto.subscription.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.payment.api.dto.SubscriptionSummary;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * GET /api/subscriptions 의 data 필드 모양.
 *
 * ⚠️ jackson-datatype-jsr310 모듈이 클래스패스에 없으면 LocalDateTime 필드가
 * 이상하게(중첩 객체) 직렬화된다. bff-service의 build.gradle에
 * implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.4'
 * 를 추가해야 한다 (api/build.gradle의 jackson-databind:2.9.4와 버전 맞춤).
 *
 * jsr310 모듈이 있어도 기본 설정으로는 날짜가 배열([2026,7,16,0,0])로 나가서,
 * 필드별로 @JsonFormat을 붙여 ISO 문자열("2026-07-16T00:00:00")로 강제한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionResponse {

    private Long subscriptionId;
    private String planCode;
    private SubscriptionStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endDate;

    private Boolean autoRenewYn;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelRequestedAt;

    private String paymentMethod;
    private String paymentLabel;
    private String paymentMasked;

    public static SubscriptionResponse from(SubscriptionSummary summary) {
        SubscriptionResponse response = new SubscriptionResponse();
        response.subscriptionId = summary.getSubscriptionId();
        response.planCode = summary.getPlanCode();
        response.status = summary.getStatus() == null
                ? null : SubscriptionStatus.valueOf(summary.getStatus());
        response.startDate = summary.getStartDate();
        response.endDate = summary.getEndDate();
        response.autoRenewYn = summary.getAutoRenewYn();
        response.cancelRequestedAt = summary.getCancelRequestedAt();
        response.paymentMethod = summary.getPaymentMethod();
        response.paymentLabel = summary.getPaymentLabel();
        response.paymentMasked = summary.getPaymentMasked();
        return response;
    }
}
