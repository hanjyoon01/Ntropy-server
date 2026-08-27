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
public class PaymentSummary {
    private Long paymentId;
    private String planCode;
    private Long amount;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private String paymentStatus;
    private String failureReason;
    private String receiptUrl;
}
