package com.ntropy.payment.config;

import com.ntropy.payment.domain.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortOneProperties {

    @Value("${portone.store-id:}")
    private String storeId;

    @Value("${portone.api-secret:}")
    private String apiSecret;

    @Value("${portone.channel-key.card:}")
    private String cardChannelKey;

    @Value("${portone.channel-key.kakaopay:}")
    private String kakaopayChannelKey;

    @Value("${portone.channel-key.tosspay:}")
    private String tosspayChannelKey;

    @Value("${portone.webhook-secret:}")
    private String webhookSecret;


    public String getStoreId() {
        return storeId;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public String getChannelKey(PaymentMethod method) {
        switch (method) {
            case CARD:
                return cardChannelKey;
            case KAKAOPAY:
                return kakaopayChannelKey;
            case TOSSPAY:
                return tosspayChannelKey;
            default:
                throw new IllegalArgumentException("지원하지 않는 결제수단: " + method);
        }
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}