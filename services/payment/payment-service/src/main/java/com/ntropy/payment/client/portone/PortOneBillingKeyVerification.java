package com.ntropy.payment.client.portone;

import com.ntropy.payment.domain.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PortOneBillingKeyVerification {
    private final boolean valid;
    private final PaymentMethod paymentMethod;
    private final String paymentLabel;
    private final String paymentMasked;
}