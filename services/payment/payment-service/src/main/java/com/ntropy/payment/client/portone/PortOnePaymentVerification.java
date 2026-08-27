package com.ntropy.payment.client.portone;

import com.ntropy.payment.domain.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PortOnePaymentVerification {

    private final boolean paid;

    private final long amount;

    private final PaymentMethod paymentMethod;

    private final String paymentLabel;

    private final String paymentMasked;

    private final String receiptUrl;
}