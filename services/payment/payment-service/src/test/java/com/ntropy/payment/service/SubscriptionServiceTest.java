package com.ntropy.payment.service;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.payment.client.portone.PortOneBillingKeyClient;
import com.ntropy.payment.client.portone.PortOneBillingKeyVerification;
import com.ntropy.payment.client.portone.PortOnePaymentClient;
import com.ntropy.payment.client.portone.PortOnePaymentVerification;
import com.ntropy.payment.client.portone.PortOneWebhookVerifier;
import com.ntropy.payment.domain.Payment;
import com.ntropy.payment.domain.PaymentMethod;
import com.ntropy.payment.domain.PaymentStatus;
import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.domain.SubscriptionStatus;
import com.ntropy.payment.mapper.PaymentMapper;
import com.ntropy.payment.mapper.SubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final Long USER_ID = 1L;
    private static final String BILLING_KEY = "billing-key";

    @Mock
    private SubscriptionMapper subscriptionMapper;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PortOnePaymentClient paymentClient;
    @Mock
    private PortOneBillingKeyClient billingKeyClient;
    @Mock
    private PortOneWebhookVerifier webhookVerifier;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(
                subscriptionMapper, paymentMapper, paymentClient, billingKeyClient, webhookVerifier);
        lenient().when(paymentClient.schedulePayment(anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(true);
        lenient().when(paymentClient.cancelScheduledPayments(anyString())).thenReturn(true);
    }

    @Test
    void updatePaymentMethodCancelsOldScheduleAndSchedulesWithNewBillingKey() {
        String newBillingKey = "new-billing-key";
        Subscription subscription = activeSubscription();
        PortOneBillingKeyVerification verification = new PortOneBillingKeyVerification(
                true, PaymentMethod.KAKAOPAY, "카카오페이", null);
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);
        when(billingKeyClient.verifyBillingKey(newBillingKey)).thenReturn(verification);

        Subscription result = service.updatePaymentMethod(USER_ID, newBillingKey);

        assertSame(subscription, result);
        assertEquals(newBillingKey, result.getCustomerUid());
        assertEquals(PaymentMethod.KAKAOPAY, result.getPaymentMethod());
        assertEquals("카카오페이", result.getPaymentLabel());
        assertNull(result.getPaymentMasked());
        verify(paymentClient).cancelScheduledPayments(BILLING_KEY);
        verify(paymentMapper).cancelPendingBySubscriptionId(10L);
        verify(subscriptionMapper).update(subscription);
        verify(paymentMapper).insert(org.mockito.ArgumentMatchers.argThat(
                scheduled -> scheduled.getPaymentStatus() == PaymentStatus.PENDING));
        verify(paymentClient).schedulePayment(
                anyString(), eq(newBillingKey), eq(4_900L), anyString(), eq(subscription.getEndDate()));
    }

    @Test
    void updatePaymentMethodRejectsNonActiveSubscription() {
        Subscription subscription = activeSubscription();
        subscription.setStatus(SubscriptionStatus.CANCEL_SCHEDULED);
        subscription.setAutoRenewYn(false);
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.updatePaymentMethod(USER_ID, "new-billing-key"));

        assertEquals(409, exception.getStatusCode());
        verify(billingKeyClient, never()).verifyBillingKey(anyString());
        verify(paymentClient, never()).cancelScheduledPayments(anyString());
        verify(subscriptionMapper, never()).update(any());
    }

    @Test
    void updatePaymentMethodStopsWhenOldScheduleCancellationFails() {
        String newBillingKey = "new-billing-key";
        Subscription subscription = activeSubscription();
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);
        when(billingKeyClient.verifyBillingKey(newBillingKey)).thenReturn(validBillingKey());
        when(paymentClient.cancelScheduledPayments(BILLING_KEY)).thenReturn(false);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.updatePaymentMethod(USER_ID, newBillingKey));

        assertEquals(502, exception.getStatusCode());
        assertEquals(BILLING_KEY, subscription.getCustomerUid());
        verify(paymentMapper, never()).cancelPendingBySubscriptionId(anyLong());
        verify(subscriptionMapper, never()).update(any());
        verify(paymentClient, never()).schedulePayment(anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void updatePaymentMethodRaisesErrorWhenNewScheduleFails() {
        String newBillingKey = "new-billing-key";
        Subscription subscription = activeSubscription();
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);
        when(billingKeyClient.verifyBillingKey(newBillingKey)).thenReturn(validBillingKey());
        when(paymentClient.schedulePayment(anyString(), eq(newBillingKey), eq(4_900L), anyString(), eq(subscription.getEndDate())))
                .thenReturn(false);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.updatePaymentMethod(USER_ID, newBillingKey));

        assertEquals(502, exception.getStatusCode());
        verify(paymentClient).cancelScheduledPayments(BILLING_KEY);
        verify(paymentMapper).cancelPendingBySubscriptionId(10L);
        verify(subscriptionMapper).update(subscription);
    }

    @Test
    void initSubscriptionCreatesSubscriptionInitialPaymentAndSchedule() {
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(null);
        when(billingKeyClient.verifyBillingKey(BILLING_KEY)).thenReturn(validBillingKey());
        when(paymentClient.payWithBillingKey(anyString(), eq(BILLING_KEY), eq(4_900L), anyString()))
                .thenReturn(successfulPayment());
        doAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setSubscriptionId(10L);
            return 1;
        }).when(subscriptionMapper).insert(any(Subscription.class));

        Subscription result = service.initSubscription(USER_ID, BILLING_KEY);

        assertEquals(10L, result.getSubscriptionId());
        assertEquals(PlanCode.PRO, result.getPlanCode());
        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        assertEquals(BILLING_KEY, result.getCustomerUid());
        assertTrue(result.getAutoRenewYn());
        assertNotNull(result.getStartDate());
        assertNotNull(result.getEndDate());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper, org.mockito.Mockito.times(2)).insert(paymentCaptor.capture());
        Payment initialPayment = paymentCaptor.getAllValues().get(0);
        Payment scheduledPayment = paymentCaptor.getAllValues().get(1);
        assertEquals(PaymentStatus.SUCCESS, initialPayment.getPaymentStatus());
        assertEquals(PaymentStatus.PENDING, scheduledPayment.getPaymentStatus());
        assertEquals(10L, scheduledPayment.getSubscriptionId());
        assertEquals(34, scheduledPayment.getMerchantUid().length());
        assertTrue(scheduledPayment.getMerchantUid().matches("SR[0-9a-f]{32}"));
        verify(paymentClient).schedulePayment(
                eq(scheduledPayment.getMerchantUid()), eq(BILLING_KEY), eq(4_900L), anyString(), eq(result.getEndDate()));
    }

    @Test
    void initSubscriptionRejectsAlreadyUsableSubscription() {
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(activeSubscription());

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.initSubscription(USER_ID, BILLING_KEY));

        assertEquals(409, exception.getStatusCode());
        verify(billingKeyClient, never()).verifyBillingKey(anyString());
        verify(paymentClient, never()).payWithBillingKey(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void initSubscriptionRejectsInvalidBillingKey() {
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(null);
        when(billingKeyClient.verifyBillingKey(BILLING_KEY))
                .thenReturn(new PortOneBillingKeyVerification(false, null, null, null));

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.initSubscription(USER_ID, BILLING_KEY));

        assertEquals(400, exception.getStatusCode());
        verify(paymentClient, never()).payWithBillingKey(anyString(), anyString(), anyLong(), anyString());
        verify(subscriptionMapper, never()).insert(any());
    }

    @Test
    void initSubscriptionRejectsIncompletePayment() {
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(null);
        when(billingKeyClient.verifyBillingKey(BILLING_KEY)).thenReturn(validBillingKey());
        when(paymentClient.payWithBillingKey(anyString(), eq(BILLING_KEY), eq(4_900L), anyString()))
                .thenReturn(new PortOnePaymentVerification(false, 4_900L, null, null, null, null));

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.initSubscription(USER_ID, BILLING_KEY));

        assertEquals(400, exception.getStatusCode());
        verify(subscriptionMapper, never()).insert(any());
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    void handleScheduledPaymentResultIgnoresAlreadyProcessedPayment() {
        Payment payment = payment(PaymentStatus.SUCCESS);
        when(paymentMapper.findByMerchantUid("payment-id")).thenReturn(payment);

        service.handleScheduledPaymentResult("payment-id");

        verify(paymentClient, never()).verifyPayment(anyString());
        verify(paymentMapper, never()).update(any());
    }

    @Test
    void successfulScheduledPaymentExtendsSubscriptionAndCreatesNextSchedule() {
        Payment pending = payment(PaymentStatus.PENDING);
        Subscription subscription = activeSubscription();
        LocalDateTime previousEndDate = subscription.getEndDate();
        when(paymentMapper.findByMerchantUid("payment-id")).thenReturn(pending);
        when(paymentClient.verifyPayment("payment-id")).thenReturn(successfulPayment());
        when(subscriptionMapper.findById(10L)).thenReturn(subscription);

        service.handleScheduledPaymentResult("payment-id");

        assertEquals(PaymentStatus.SUCCESS, pending.getPaymentStatus());
        assertEquals(previousEndDate.plusMonths(1), subscription.getEndDate());
        verify(paymentMapper).update(pending);
        verify(subscriptionMapper).update(subscription);
        verify(paymentMapper).insert(org.mockito.ArgumentMatchers.argThat(
                next -> next.getPaymentStatus() == PaymentStatus.PENDING));
        verify(paymentClient).schedulePayment(anyString(), eq(BILLING_KEY), eq(4_900L), anyString(), eq(subscription.getEndDate()));
    }

    @Test
    void failedScheduledPaymentCreatesRetryBeforeThirdFailure() {
        Payment pending = payment(PaymentStatus.PENDING);
        Subscription subscription = activeSubscription();
        when(paymentMapper.findByMerchantUid("payment-id")).thenReturn(pending);
        when(paymentClient.verifyPayment("payment-id"))
                .thenReturn(new PortOnePaymentVerification(false, 4_900L, null, null, null, null));
        when(subscriptionMapper.findById(10L)).thenReturn(subscription);
        when(paymentMapper.findAllBySubscriptionId(10L)).thenReturn(List.of(pending));

        service.handleScheduledPaymentResult("payment-id");

        assertEquals(PaymentStatus.FAILED, pending.getPaymentStatus());
        assertNotNull(pending.getFailureReason());
        verify(paymentMapper).insert(org.mockito.ArgumentMatchers.argThat(
                retry -> retry.getPaymentStatus() == PaymentStatus.PENDING));
        verify(subscriptionMapper, never()).update(subscription);
    }

    @Test
    void thirdConsecutiveFailureExpiresSubscriptionWithoutAnotherRetry() {
        Payment current = payment(PaymentStatus.PENDING);
        Payment previous1 = payment(PaymentStatus.FAILED);
        Payment previous2 = payment(PaymentStatus.FAILED);
        Subscription subscription = activeSubscription();
        when(paymentMapper.findByMerchantUid("payment-id")).thenReturn(current);
        when(paymentClient.verifyPayment("payment-id"))
                .thenReturn(new PortOnePaymentVerification(false, 4_900L, null, null, null, null));
        when(subscriptionMapper.findById(10L)).thenReturn(subscription);
        when(paymentMapper.findAllBySubscriptionId(10L)).thenReturn(List.of(current, previous1, previous2));

        service.handleScheduledPaymentResult("payment-id");

        assertEquals(SubscriptionStatus.EXPIRED, subscription.getStatus());
        verify(subscriptionMapper).update(subscription);
        verify(paymentMapper, never()).insert(any());
        verify(paymentClient, never()).schedulePayment(anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void receiveWebhookRejectsInvalidSignature() {
        when(webhookVerifier.verify("id", "timestamp", "signature", "body")).thenReturn(false);

        boolean result = service.receiveWebhook("id", "timestamp", "signature", "body");

        assertFalse(result);
        verify(paymentMapper, never()).findByMerchantUid(anyString());
    }

    @Test
    void cancelSubscriptionCancelsScheduleAndKeepsAccessUntilEndDate() {
        Subscription subscription = activeSubscription();
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);

        Subscription result = service.cancelSubscription(USER_ID);

        assertSame(subscription, result);
        assertEquals(SubscriptionStatus.CANCEL_SCHEDULED, result.getStatus());
        assertFalse(result.getAutoRenewYn());
        assertNotNull(result.getCancelRequestedAt());
        assertTrue(result.isUsable());
        verify(paymentClient).cancelScheduledPayments(BILLING_KEY);
        verify(paymentMapper).cancelPendingBySubscriptionId(10L);
        verify(subscriptionMapper).update(subscription);
    }

    @Test
    void cancelSubscriptionDoesNotChangeDatabaseWhenScheduleCancellationFails() {
        Subscription subscription = activeSubscription();
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);
        when(paymentClient.cancelScheduledPayments(BILLING_KEY)).thenReturn(false);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.cancelSubscription(USER_ID));

        assertEquals(502, exception.getStatusCode());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertTrue(subscription.getAutoRenewYn());
        assertNull(subscription.getCancelRequestedAt());
        verify(paymentMapper, never()).cancelPendingBySubscriptionId(anyLong());
        verify(subscriptionMapper, never()).update(any());
    }

    @Test
    void revokeCancelReactivatesAndSchedulesSubscription() {
        Subscription subscription = activeSubscription();
        subscription.setStatus(SubscriptionStatus.CANCEL_SCHEDULED);
        subscription.setAutoRenewYn(false);
        subscription.setCancelRequestedAt(LocalDateTime.now().minusDays(1));
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);

        Subscription result = service.revokeCancel(USER_ID);

        assertEquals(SubscriptionStatus.ACTIVE, result.getStatus());
        assertTrue(result.getAutoRenewYn());
        assertNull(result.getCancelRequestedAt());
        verify(paymentMapper).insert(org.mockito.ArgumentMatchers.argThat(
                scheduled -> scheduled.getPaymentStatus() == PaymentStatus.PENDING));
        verify(paymentClient).schedulePayment(anyString(), eq(BILLING_KEY), eq(4_900L), anyString(), eq(subscription.getEndDate()));
    }

    @Test
    void revokeCancelRejectsExpiredSubscription() {
        Subscription subscription = activeSubscription();
        subscription.setStatus(SubscriptionStatus.CANCEL_SCHEDULED);
        subscription.setAutoRenewYn(false);
        subscription.setCancelRequestedAt(LocalDateTime.now().minusMonths(1));
        subscription.setEndDate(LocalDateTime.now().minusSeconds(1));
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.revokeCancel(USER_ID));

        assertEquals(409, exception.getStatusCode());
        assertEquals(SubscriptionStatus.CANCEL_SCHEDULED, subscription.getStatus());
        assertFalse(subscription.getAutoRenewYn());
        verify(subscriptionMapper, never()).update(any());
        verify(paymentMapper, never()).insert(any());
        verify(paymentClient, never()).schedulePayment(anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void revokeCancelRejectsSubscriptionWithoutEndDate() {
        Subscription subscription = activeSubscription();
        subscription.setStatus(SubscriptionStatus.CANCEL_SCHEDULED);
        subscription.setAutoRenewYn(false);
        subscription.setEndDate(null);
        when(subscriptionMapper.findLatestByUserId(USER_ID)).thenReturn(subscription);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.revokeCancel(USER_ID));

        assertEquals(409, exception.getStatusCode());
        verify(subscriptionMapper, never()).update(any());
        verify(paymentClient, never()).schedulePayment(anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void getPaymentHistoryCombinesSubscriptionsExcludesPendingAndSortsNewestFirst() {
        Subscription first = activeSubscription();
        Subscription second = activeSubscription();
        second.setSubscriptionId(20L);
        Payment oldSuccess = payment(PaymentStatus.SUCCESS);
        oldSuccess.setCreatedAt(LocalDateTime.now().minusMonths(2));
        Payment pending = payment(PaymentStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now());
        Payment recentFailure = payment(PaymentStatus.FAILED);
        recentFailure.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(subscriptionMapper.findAllByUserId(USER_ID)).thenReturn(List.of(first, second));
        when(paymentMapper.findAllBySubscriptionId(10L)).thenReturn(List.of(oldSuccess, pending));
        when(paymentMapper.findAllBySubscriptionId(20L)).thenReturn(List.of(recentFailure));

        List<Payment> result = service.getPaymentHistory(USER_ID);

        assertEquals(List.of(recentFailure, oldSuccess), result);
    }

    private Subscription activeSubscription() {
        Subscription subscription = new Subscription();
        subscription.setSubscriptionId(10L);
        subscription.setUserId(USER_ID);
        subscription.setPlanCode(PlanCode.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now().minusMonths(1));
        subscription.setEndDate(LocalDateTime.now().plusMonths(1));
        subscription.setAutoRenewYn(true);
        subscription.setCustomerUid(BILLING_KEY);
        subscription.setPaymentMethod(PaymentMethod.CARD);
        return subscription;
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setPaymentId(100L);
        payment.setSubscriptionId(10L);
        payment.setPlanCode(PlanCode.PRO);
        payment.setMerchantUid("payment-id");
        payment.setAmount(4_900L);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setPaymentStatus(status);
        return payment;
    }

    private PortOneBillingKeyVerification validBillingKey() {
        return new PortOneBillingKeyVerification(true, PaymentMethod.CARD, "신한카드", "1234-****-****-5678");
    }

    private PortOnePaymentVerification successfulPayment() {
        return new PortOnePaymentVerification(
                true, 4_900L, PaymentMethod.CARD, "신한카드", "1234-****-****-5678", "https://receipt");
    }
}
