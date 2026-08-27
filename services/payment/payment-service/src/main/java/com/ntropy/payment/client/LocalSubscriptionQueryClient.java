package com.ntropy.payment.client;

import com.ntropy.common.domain.Feature;
import com.ntropy.payment.api.client.SubscriptionCommandClient;
import com.ntropy.payment.api.client.SubscriptionQueryClient;
import com.ntropy.payment.api.dto.PaymentSummary;
import com.ntropy.payment.api.dto.PaymentConfigSummary;
import com.ntropy.payment.api.dto.PlanSummary;
import com.ntropy.payment.api.dto.SubscriptionSummary;
import com.ntropy.payment.domain.Payment;
import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.domain.PaymentMethod;
import com.ntropy.payment.config.PortOneProperties;
import com.ntropy.payment.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LocalSubscriptionQueryClient implements SubscriptionQueryClient, SubscriptionCommandClient {

    private final SubscriptionService subscriptionService;
    private final PortOneProperties portOneProperties;

    @Autowired
    public LocalSubscriptionQueryClient(SubscriptionService subscriptionService,
                                        PortOneProperties portOneProperties) {
        this.subscriptionService = subscriptionService;
        this.portOneProperties = portOneProperties;
    }

    @Override
    public List<PlanSummary> getPlans() {
        return subscriptionService.getAllPlans().stream()
                .map(this::toPlanSummary)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentConfigSummary getPaymentConfig(Long userId) {
        Map<String, String> channels = new LinkedHashMap<>();
        channels.put(PaymentMethod.CARD.name(), portOneProperties.getChannelKey(PaymentMethod.CARD));
        channels.put(PaymentMethod.KAKAOPAY.name(), portOneProperties.getChannelKey(PaymentMethod.KAKAOPAY));
        channels.put(PaymentMethod.TOSSPAY.name(), portOneProperties.getChannelKey(PaymentMethod.TOSSPAY));
        return new PaymentConfigSummary(portOneProperties.getStoreId(), channels, "user-" + userId);
    }

    @Override
    public SubscriptionSummary getMySubscription(Long userId) {
        return toSubscriptionSummary(subscriptionService.getMySubscription(userId));
    }

    @Override
    public boolean supportsFeature(Long userId, Feature feature) {
        Subscription subscription = subscriptionService.getMySubscription(userId);
        return subscription.supports(feature);
    }

    @Override
    public SubscriptionSummary initSubscription(Long userId, String billingKey) {
        Subscription subscription = subscriptionService.initSubscription(userId, billingKey);
        return toSubscriptionSummary(subscription);
    }

    private PlanSummary toPlanSummary(PlanCode planCode) {
        return new PlanSummary(
                planCode.name(),
                planCode.getDisplayName(),
                planCode.getMonthlyPrice(),
                planCode.getFeatureLabels()
        );
    }

    private SubscriptionSummary toSubscriptionSummary(Subscription s) {
        return new SubscriptionSummary(
                s.getSubscriptionId(),
                s.getPlanCode() != null ? s.getPlanCode().name() : null,
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getStartDate(),
                s.getEndDate(),
                s.getAutoRenewYn(),
                s.getCancelRequestedAt(),
                s.getPaymentMethod() != null ? s.getPaymentMethod().name() : null,
                s.getPaymentLabel(),
                s.getPaymentMasked()
        );
    }

    @Override
    public SubscriptionSummary updatePaymentMethod(Long userId, String billingKey) {
        Subscription subscription = subscriptionService.updatePaymentMethod(userId, billingKey);
        return toSubscriptionSummary(subscription);
    }

    @Override
    public void handleScheduledPaymentResult(String paymentId) {
        subscriptionService.handleScheduledPaymentResult(paymentId);
    }


    @Override
    public boolean receiveWebhook(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody) {
        return subscriptionService.receiveWebhook(webhookId, webhookTimestamp, webhookSignature, rawBody);
    }

    @Override
    public List<PaymentSummary> getPaymentHistory(Long userId) {
        return subscriptionService.getPaymentHistory(userId).stream()
                .map(this::toPaymentSummary)
                .collect(Collectors.toList());
    }

    @Override
    public SubscriptionSummary cancelSubscription(Long userId) {
        Subscription subscription = subscriptionService.cancelSubscription(userId);
        return toSubscriptionSummary(subscription);
    }

    @Override
    public SubscriptionSummary revokeCancel(Long userId) {
        Subscription subscription = subscriptionService.revokeCancel(userId);
        return toSubscriptionSummary(subscription);
    }

    private PaymentSummary toPaymentSummary(Payment p) {
        return new PaymentSummary(
                p.getPaymentId(),
                p.getPlanCode() != null ? p.getPlanCode().name() : null,
                p.getAmount(),
                p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null,
                p.getCreatedAt(),
                p.getPaymentStatus() != null ? p.getPaymentStatus().name() : null,
                p.getFailureReason(),
                p.getReceiptUrl()
        );
    }
}
