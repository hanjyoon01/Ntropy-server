package com.ntropy.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.payment.client.portone.PortOneBillingKeyClient;
import com.ntropy.payment.client.portone.PortOneBillingKeyVerification;
import com.ntropy.payment.client.portone.PortOnePaymentClient;
import com.ntropy.payment.client.portone.PortOnePaymentVerification;
import com.ntropy.payment.client.portone.PortOneWebhookVerifier;
import com.ntropy.payment.domain.Payment;
import com.ntropy.payment.domain.PaymentStatus;
import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.domain.SubscriptionStatus;
import com.ntropy.payment.exception.PaymentErrorCode;
import com.ntropy.payment.mapper.PaymentMapper;
import com.ntropy.payment.mapper.SubscriptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL_DAYS = 1;

    private final SubscriptionMapper subscriptionMapper;
    private final PaymentMapper paymentMapper;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PortOneBillingKeyClient portOneBillingKeyClient;
    private final PortOneWebhookVerifier portOneWebhookVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SubscriptionService(SubscriptionMapper subscriptionMapper,
                               PaymentMapper paymentMapper,
                               PortOnePaymentClient portOnePaymentClient,
                               PortOneBillingKeyClient portOneBillingKeyClient,
                               PortOneWebhookVerifier portOneWebhookVerifier) {
        this.subscriptionMapper = subscriptionMapper;
        this.paymentMapper = paymentMapper;
        this.portOnePaymentClient = portOnePaymentClient;
        this.portOneBillingKeyClient = portOneBillingKeyClient;
        this.portOneWebhookVerifier = portOneWebhookVerifier;
    }

    public List<PlanCode> getAllPlans() {
        return Arrays.asList(PlanCode.values());
    }


    @Transactional
    public Subscription initSubscription(Long userId, String billingKey) {
        Subscription existing = subscriptionMapper.findLatestByUserId(userId);
        if (existing != null && existing.isUsable()) {
            throw new ServiceException(PaymentErrorCode.ALREADY_SUBSCRIBED);
        }

        PortOneBillingKeyVerification billingKeyVerification = portOneBillingKeyClient.verifyBillingKey(billingKey);
        if (!billingKeyVerification.isValid()) {
            throw new ServiceException(PaymentErrorCode.INVALID_BILLING_KEY);
        }

        String paymentId = "sub-init-" + UUID.randomUUID();
        PortOnePaymentVerification paymentResult = portOnePaymentClient.payWithBillingKey(
                paymentId, billingKey, PlanCode.PRO.getMonthlyPrice(), "Ntropy Pro 구독 최초결제");

        if (!paymentResult.isPaid()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_NOT_COMPLETED);
        }

        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanCode(PlanCode.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(1));
        subscription.setAutoRenewYn(true);
        subscription.setCustomerUid(billingKey);
        subscription.setPaymentMethod(paymentResult.getPaymentMethod());
        subscription.setPaymentLabel(paymentResult.getPaymentLabel());
        subscription.setPaymentMasked(paymentResult.getPaymentMasked());
        subscriptionMapper.insert(subscription);

        Payment payment = new Payment();
        payment.setSubscriptionId(subscription.getSubscriptionId());
        payment.setPlanCode(PlanCode.PRO);
        payment.setAmount(paymentResult.getAmount());
        payment.setPaymentMethod(paymentResult.getPaymentMethod());
        payment.setCreatedAt(now);
        payment.setMerchantUid(paymentId);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setReceiptUrl(paymentResult.getReceiptUrl());
        paymentMapper.insert(payment);

        scheduleUpcomingPayment(subscription, subscription.getEndDate());

        return subscription;
    }

    @Transactional
    public Subscription updatePaymentMethod(Long userId, String billingKey) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        if (subscription == null) {
            throw new ServiceException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                || !subscription.isUsable()
                || !Boolean.TRUE.equals(subscription.getAutoRenewYn())) {
            throw new ServiceException(PaymentErrorCode.SUBSCRIPTION_NOT_ACTIVE);
        }

        PortOneBillingKeyVerification verification = portOneBillingKeyClient.verifyBillingKey(billingKey);
        if (!verification.isValid()) {
            throw new ServiceException(PaymentErrorCode.INVALID_BILLING_KEY);
        }

        if (!portOnePaymentClient.cancelScheduledPayments(subscription.getCustomerUid())) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_SCHEDULE_CANCEL_FAILED);
        }

        paymentMapper.cancelPendingBySubscriptionId(subscription.getSubscriptionId());

        subscription.setCustomerUid(billingKey);
        subscription.setPaymentMethod(verification.getPaymentMethod());
        subscription.setPaymentLabel(verification.getPaymentLabel());
        subscription.setPaymentMasked(verification.getPaymentMasked());
        subscriptionMapper.update(subscription);

        scheduleUpcomingPayment(subscription, subscription.getEndDate());

        return subscription;
    }

    @Transactional
    public void handleScheduledPaymentResult(String paymentId) {
        Payment pendingPayment = paymentMapper.findByMerchantUid(paymentId);
        if (pendingPayment == null) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        if (pendingPayment.getPaymentStatus() != PaymentStatus.PENDING) {
            // 이미 SUCCESS/FAILED로 처리된 건 - 웹훅 중복수신. 재처리하지 않고 그대로 종료.
            return;
        }

        PortOnePaymentVerification result = portOnePaymentClient.verifyPayment(paymentId);

        if (result.isPaid()) {
            handleScheduledPaymentSuccess(pendingPayment, result);
        } else {
            handleScheduledPaymentFailure(pendingPayment);
        }
    }

    @Transactional
    public boolean receiveWebhook(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody) {
        if (!portOneWebhookVerifier.verify(webhookId, webhookTimestamp, webhookSignature, rawBody)) {
            return false;
        }

        String paymentId = extractPaymentId(rawBody);
        if (paymentId != null && paymentMapper.findByMerchantUid(paymentId) != null) {
            handleScheduledPaymentResult(paymentId);
        }

        return true;
    }

    private String extractPaymentId(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode paymentIdNode = root.path("data").path("paymentId");
            return paymentIdNode.isMissingNode() ? null : paymentIdNode.asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleScheduledPaymentSuccess(Payment pendingPayment, PortOnePaymentVerification result) {
        pendingPayment.setAmount(result.getAmount());
        pendingPayment.setPaymentMethod(result.getPaymentMethod());
        pendingPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        pendingPayment.setFailureReason(null);
        pendingPayment.setReceiptUrl(result.getReceiptUrl());
        paymentMapper.update(pendingPayment);

        Subscription subscription = subscriptionMapper.findById(pendingPayment.getSubscriptionId());
        subscription.setEndDate(subscription.getEndDate().plusMonths(1));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPaymentMethod(result.getPaymentMethod());
        subscription.setPaymentLabel(result.getPaymentLabel());
        subscription.setPaymentMasked(result.getPaymentMasked());
        subscriptionMapper.update(subscription);

        scheduleUpcomingPayment(subscription, subscription.getEndDate());
    }

    private void handleScheduledPaymentFailure(Payment pendingPayment) {
        pendingPayment.setPaymentStatus(PaymentStatus.FAILED);
        pendingPayment.setFailureReason("포트원 결제 실패");
        paymentMapper.update(pendingPayment);

        Subscription subscription = subscriptionMapper.findById(pendingPayment.getSubscriptionId());
        int consecutiveFailures = countConsecutiveFailures(subscription.getSubscriptionId());

        if (consecutiveFailures < MAX_RETRY_COUNT) {
            scheduleUpcomingPayment(subscription, LocalDateTime.now().plusDays(RETRY_INTERVAL_DAYS));
        } else {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionMapper.update(subscription);
            // TODO(notification-service 연계 지점): 정기결제 최종 실패로 구독이 만료됐다는 알림 발송
        }
    }

    private int countConsecutiveFailures(Long subscriptionId) {
        List<Payment> history = paymentMapper.findAllBySubscriptionId(subscriptionId);
        int count = 0;
        for (Payment payment : history) {
            if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private void scheduleUpcomingPayment(Subscription subscription, LocalDateTime timeToPay) {
        String paymentId = "SR" + UUID.randomUUID().toString().replace("-", "");

        Payment pendingPayment = new Payment();
        pendingPayment.setSubscriptionId(subscription.getSubscriptionId());
        pendingPayment.setPlanCode(PlanCode.PRO);
        pendingPayment.setAmount((long) PlanCode.PRO.getMonthlyPrice());
        pendingPayment.setCreatedAt(LocalDateTime.now());
        pendingPayment.setMerchantUid(paymentId);
        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        paymentMapper.insert(pendingPayment);

        boolean scheduled = portOnePaymentClient.schedulePayment(
                paymentId, subscription.getCustomerUid(), PlanCode.PRO.getMonthlyPrice(),
                "Ntropy Pro 정기결제", timeToPay);
        if (!scheduled) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_SCHEDULE_FAILED);
        }
    }

    private Subscription defaultBasicSubscription() {
        Subscription basic = new Subscription();
        basic.setPlanCode(PlanCode.BASIC);
        basic.setStatus(SubscriptionStatus.ACTIVE);
        basic.setAutoRenewYn(false);
        return basic;
    }

    /**
     * 유저의 현재 구독 상태를 조회한다.
     * ⚠️ SUBSCRIPTION02_F03(만료 시 최종 해지 처리) 구현 방식: 배치/스케줄러 대신
     * "조회 시점 지연갱신"으로 결정했다. CANCEL_SCHEDULED(해지예약) 상태인 구독은
     * 다음 회차 예약 자체를 취소해뒀기 때문에, 웹훅이 다시 올 일이 없어 상태가
     * 영원히 CANCEL_SCHEDULED로 남아있을 수 있다. 그래서 조회할 때마다 만료일이
     * 지났는지 확인해서, 지났으면 이 자리에서 EXPIRED로 갱신한다.
     */
    public Subscription getMySubscription(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        if (subscription == null) {
            return defaultBasicSubscription();
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCEL_SCHEDULED
                && subscription.getEndDate() != null
                && subscription.getEndDate().isBefore(LocalDateTime.now())) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionMapper.update(subscription);
        }

        return subscription;
    }

    /**
     * 해지예약(SUBSCRIPTION02_F01 관련) - POST /api/subscriptions/cancel.
     *
     * 순서:
     * 1) 활성 구독이 있어야 한다 (없거나 이미 만료됐으면 SUBSCRIPTION_NOT_FOUND).
     * 2) 이미 해지예약 상태면 중복 요청으로 보고 거절한다 (ALREADY_CANCELLED).
     * 3) 포트원에 걸려있는 다음 회차 예약을 먼저 취소한다 - 이 호출이 실패하면
     *    (예외가 던져지면) @Transactional에 의해 아래 DB 변경도 전부 롤백된다.
     *    순서를 "포트원 취소 → DB 반영"으로 둔 이유: DB만 먼저 바꿔놓고 포트원 취소에
     *    실패하면, 사용자는 해지된 줄 알지만 실제로는 다음 달에 또 결제되는 사고로
     *    이어질 수 있기 때문이다.
     * 4) status를 CANCEL_SCHEDULED로, cancel_requested_at을 지금 시각으로,
     *    auto_renew_yn을 false로 바꾼다. end_date는 그대로 둔다 - 만료일까지는
     *    계속 이용 가능해야 하기 때문(isUsable()이 CANCEL_SCHEDULED를 true로 취급).
     */
    @Transactional
    public Subscription cancelSubscription(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        if (subscription == null || !subscription.isUsable()) {
            throw new ServiceException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        if (subscription.getStatus() == SubscriptionStatus.CANCEL_SCHEDULED) {
            throw new ServiceException(PaymentErrorCode.ALREADY_CANCELLED);
        }

        if (!portOnePaymentClient.cancelScheduledPayments(subscription.getCustomerUid())) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_SCHEDULE_CANCEL_FAILED);
        }

        paymentMapper.cancelPendingBySubscriptionId(subscription.getSubscriptionId());

        subscription.setStatus(SubscriptionStatus.CANCEL_SCHEDULED);
        subscription.setCancelRequestedAt(LocalDateTime.now());
        subscription.setAutoRenewYn(false);
        subscriptionMapper.update(subscription);

        return subscription;
    }

    /**
     * 해지예약 취소 - DELETE /api/subscriptions/cancel.
     * status를 ACTIVE로 되돌리고, cancelSubscription 때 취소했던 다음 회차 결제를
     * 다시 예약한다 (기존 end_date 기준 그대로 - 해지예약 중엔 end_date를 안 건드렸으므로).
     */
    @Transactional
    public Subscription revokeCancel(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.CANCEL_SCHEDULED) {
            throw new ServiceException(PaymentErrorCode.NOT_CANCEL_SCHEDULED);
        }
        if (subscription.getEndDate() == null
                || !subscription.getEndDate().isAfter(LocalDateTime.now())) {
            throw new ServiceException(PaymentErrorCode.CANCEL_REVOCATION_EXPIRED);
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCancelRequestedAt(null);
        subscription.setAutoRenewYn(true);
        subscriptionMapper.update(subscription);

        scheduleUpcomingPayment(subscription, subscription.getEndDate());

        return subscription;
    }

    /**
     * GET /api/subscriptions/payments - 이 유저의 전체 결제 이력(최신순).
     * 과거에 해지 후 재구독해서 SUBSCRIPTION 행이 여러 개 쌓여있어도 전부 합쳐서 보여준다.
     * PENDING(아직 결과 안 나온 정기결제 예약)은 사용자에게 보여줄 이유가 없어 제외한다.
     */
    public List<Payment> getPaymentHistory(Long userId) {
        List<Subscription> subscriptions = subscriptionMapper.findAllByUserId(userId);

        return subscriptions.stream()
                .flatMap(subscription -> paymentMapper.findAllBySubscriptionId(subscription.getSubscriptionId()).stream())
                .filter(payment -> payment.getPaymentStatus() != PaymentStatus.PENDING)
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }
}
