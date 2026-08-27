package com.ntropy.payment.api.client;

import com.ntropy.payment.api.dto.SubscriptionSummary;

public interface SubscriptionCommandClient {

    SubscriptionSummary initSubscription(Long userId, String billingKey);
    SubscriptionSummary updatePaymentMethod(Long userId, String billingKey);
    void handleScheduledPaymentResult(String paymentId);
    boolean receiveWebhook(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody);
    SubscriptionSummary cancelSubscription(Long userId);
    SubscriptionSummary revokeCancel(Long userId);
}
