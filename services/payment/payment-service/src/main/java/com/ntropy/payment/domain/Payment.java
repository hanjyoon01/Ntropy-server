package com.ntropy.payment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Payment {

    private Long paymentId;
    private Long subscriptionId;
    private PlanCode planCode;
    private String impUid;
    private Long amount;
    private PaymentMethod paymentMethod;
    private LocalDateTime createdAt;
    private String merchantUid;
    private PaymentStatus paymentStatus;
    private String failureReason;
    private String receiptUrl;
}