package com.ntropy.payment.client.portone;

import java.time.LocalDateTime;

public interface PortOnePaymentClient {

    PortOnePaymentVerification verifyPayment(String paymentId);
    PortOnePaymentVerification payWithBillingKey(String paymentId, String billingKey, long amount, String orderName);
    boolean schedulePayment(String paymentId, String billingKey, long amount, String orderName, LocalDateTime timeToPay);
    boolean cancelScheduledPayments(String billingKey);
}