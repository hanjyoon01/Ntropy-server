package com.ntropy.payment.api.client;

import com.ntropy.common.domain.Feature;
import com.ntropy.payment.api.dto.PaymentSummary;
import com.ntropy.payment.api.dto.PaymentConfigSummary;
import com.ntropy.payment.api.dto.PlanSummary;
import com.ntropy.payment.api.dto.SubscriptionSummary;

import java.util.List;

public interface SubscriptionQueryClient {

    List<PlanSummary> getPlans();

    PaymentConfigSummary getPaymentConfig(Long userId);

    SubscriptionSummary getMySubscription(Long userId);

    boolean supportsFeature(Long userId, Feature feature);

    List<PaymentSummary> getPaymentHistory(Long userId);
}
