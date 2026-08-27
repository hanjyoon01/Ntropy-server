package com.ntropy.bff.dto.subscription.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.payment.api.dto.PaymentSummary;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PaymentHistoryItemResponse {

    private Long paymentId;
    private String planCode;
    private Long amount;
    private String paymentMethod;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private PaymentStatus paymentStatus;
    private String failureReason;
    private String receiptUrl;

    public static PaymentHistoryItemResponse from(PaymentSummary summary) {
        PaymentHistoryItemResponse response = new PaymentHistoryItemResponse();
        response.paymentId = summary.getPaymentId();
        response.planCode = summary.getPlanCode();
        response.amount = summary.getAmount();
        response.paymentMethod = summary.getPaymentMethod();
        response.createdAt = summary.getCreatedAt();
        response.paymentStatus = summary.getPaymentStatus() == null
                ? null : PaymentStatus.valueOf(summary.getPaymentStatus());
        response.failureReason = summary.getFailureReason();
        response.receiptUrl = summary.getReceiptUrl();
        return response;
    }
}
