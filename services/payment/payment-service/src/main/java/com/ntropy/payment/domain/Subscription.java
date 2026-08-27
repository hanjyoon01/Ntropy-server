package com.ntropy.payment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    private Long subscriptionId;
    private Long userId;
    private PlanCode planCode;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime cancelRequestedAt;
    private Boolean autoRenewYn;
    private String customerUid;
    private PaymentMethod paymentMethod;
    private String paymentLabel;
    private String paymentMasked;
    private SubscriptionStatus status;

    public boolean isUsable() {
        boolean statusOk = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCEL_SCHEDULED;
        boolean notExpired = endDate == null || !endDate.isBefore(LocalDateTime.now());
        return statusOk && notExpired;
    }

    public boolean supports(com.ntropy.common.domain.Feature feature) {
        return isUsable() && planCode != null && planCode.supports(feature);
    }
}