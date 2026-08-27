package com.ntropy.payment.client;

import com.ntropy.payment.api.dto.PaymentConfigSummary;
import com.ntropy.payment.config.PortOneProperties;
import com.ntropy.payment.domain.PaymentMethod;
import com.ntropy.payment.service.SubscriptionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalSubscriptionQueryClientTest {

    @Test
    void paymentConfigExposesOnlyPublicPortOneIdentifiers() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        PortOneProperties properties = mock(PortOneProperties.class);
        when(properties.getStoreId()).thenReturn("store-id");
        when(properties.getChannelKey(PaymentMethod.CARD)).thenReturn("card-channel");
        when(properties.getChannelKey(PaymentMethod.KAKAOPAY)).thenReturn("kakao-channel");
        when(properties.getChannelKey(PaymentMethod.TOSSPAY)).thenReturn("toss-channel");
        LocalSubscriptionQueryClient client = new LocalSubscriptionQueryClient(subscriptionService, properties);

        PaymentConfigSummary result = client.getPaymentConfig(37L);

        assertEquals("store-id", result.getStoreId());
        assertEquals("card-channel", result.getChannels().get("CARD"));
        assertEquals("kakao-channel", result.getChannels().get("KAKAOPAY"));
        assertEquals("toss-channel", result.getChannels().get("TOSSPAY"));
        assertEquals("user-37", result.getCustomerId());
        assertEquals(3, result.getChannels().size());
        assertFalse(result.getChannels().containsKey("apiSecret"));
        assertFalse(result.getChannels().containsKey("webhookSecret"));
    }
}
