package com.ntropy.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSummary {


    private Long subscriptionId;
    private String planCode;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenewYn;
    private LocalDateTime cancelRequestedAt;
    private String paymentMethod;
    private String paymentLabel;
    private String paymentMasked;
}
