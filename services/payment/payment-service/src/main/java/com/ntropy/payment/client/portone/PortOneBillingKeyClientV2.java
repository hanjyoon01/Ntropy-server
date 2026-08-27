package com.ntropy.payment.client.portone;

import com.ntropy.payment.config.PortOneProperties;
import com.ntropy.payment.domain.PaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 포트원 V2 REST API(GET https://api.portone.io/billing-keys/{id})로 실제 검증하는 구현체.
 */
@Component
public class PortOneBillingKeyClientV2 implements PortOneBillingKeyClient {

    private static final String BASE_URL = "https://api.portone.io";

    private final PortOneProperties portOneProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public PortOneBillingKeyClientV2(PortOneProperties portOneProperties) {
        this.portOneProperties = portOneProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PortOneBillingKeyVerification verifyBillingKey(String billingKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApiSecret());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/billing-keys/" + billingKey,
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("포트원 응답이 비어있습니다. billingKey=" + billingKey);
        }

        String status = String.valueOf(body.get("status"));
        boolean valid = "ISSUED".equalsIgnoreCase(status);

        List<Map<String, Object>> methods = (List<Map<String, Object>>) body.get("methods");
        Map<String, Object> methodMap = (methods != null && !methods.isEmpty()) ? methods.get(0) : null;
        PaymentMethod paymentMethod = null;
        String paymentLabel = null;
        String paymentMasked = null;

        if (methodMap != null) {
            String type = String.valueOf(methodMap.get("type"));
            if (type.toLowerCase().contains("card")) {
                paymentMethod = PaymentMethod.CARD;
                Map<String, Object> card = (Map<String, Object>) methodMap.get("card");
                if (card != null) {
                    paymentLabel = (String) card.get("name");
                    paymentMasked = (String) card.get("number");
                }
            } else if (type.toLowerCase().contains("easypay")) {
                String provider = String.valueOf(methodMap.get("provider"));
                if (provider.toUpperCase().contains("KAKAO")) {
                    paymentMethod = PaymentMethod.KAKAOPAY;
                    paymentLabel = "카카오페이";
                } else if (provider.toUpperCase().contains("TOSS")) {
                    paymentMethod = PaymentMethod.TOSSPAY;
                    paymentLabel = "토스페이";
                }
                // 간편결제는 카드정보 자체가 안 넘어오므로 paymentMasked는 항상 null로 둔다
            }
        }

        return new PortOneBillingKeyVerification(valid, paymentMethod, paymentLabel, paymentMasked);
    }
}