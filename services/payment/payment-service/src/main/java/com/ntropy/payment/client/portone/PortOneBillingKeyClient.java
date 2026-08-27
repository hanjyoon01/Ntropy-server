package com.ntropy.payment.client.portone;

public interface PortOneBillingKeyClient {
    PortOneBillingKeyVerification verifyBillingKey(String billingKey);
}