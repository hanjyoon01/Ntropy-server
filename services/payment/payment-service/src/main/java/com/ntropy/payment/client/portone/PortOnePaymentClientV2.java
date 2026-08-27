package com.ntropy.payment.client.portone;

import com.ntropy.payment.config.PortOneProperties;
import com.ntropy.payment.domain.PaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Component
public class PortOnePaymentClientV2 implements PortOnePaymentClient {

    private static final String BASE_URL = "https://api.portone.io";

    private final PortOneProperties portOneProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public PortOnePaymentClientV2(PortOneProperties portOneProperties) {
        this.portOneProperties = portOneProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PortOnePaymentVerification verifyPayment(String paymentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApiSecret());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/payments/" + paymentId,
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("포트원 응답이 비어있습니다. paymentId=" + paymentId);
        }
        return parsePaymentResponse(body);
    }

    @Override
    public PortOnePaymentVerification payWithBillingKey(String paymentId, String billingKey, long amount, String orderName) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApiSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("total", amount);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("billingKey", billingKey);
        requestBody.put("orderName", orderName);
        requestBody.put("amount", amountMap);
        requestBody.put("currency", "KRW");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        restTemplate.exchange(
                BASE_URL + "/payments/" + paymentId + "/billing-key",
                HttpMethod.POST,
                entity,
                Map.class
        );

        return verifyPayment(paymentId);
    }

    @Override
    public boolean schedulePayment(String paymentId, String billingKey, long amount, String orderName, LocalDateTime timeToPay) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApiSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("total", amount);

        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("billingKey", billingKey);
        paymentMap.put("orderName", orderName);
        paymentMap.put("amount", amountMap);
        paymentMap.put("currency", "KRW");

        String timeToPayIso = timeToPay.atZone(ZoneId.of("Asia/Seoul")).toInstant().toString();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("payment", paymentMap);
        requestBody.put("timeToPay", timeToPayIso);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/payments/" + paymentId + "/schedule",
                HttpMethod.POST,
                entity,
                Map.class
        );

        return response.getStatusCode().is2xxSuccessful();
    }

    @SuppressWarnings("unchecked")
    private PortOnePaymentVerification parsePaymentResponse(Map<String, Object> body) {
        boolean paid = "PAID".equals(body.get("status"));

        Object amountObj = body.get("amount");
        long amount = (amountObj instanceof Map)
                ? ((Number) ((Map<String, Object>) amountObj).get("total")).longValue()
                : ((Number) amountObj).longValue();

        Map<String, Object> methodMap = (Map<String, Object>) body.get("method");
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
            }
        }

        String receiptUrl = (String) body.get("receiptUrl");

        return new PortOnePaymentVerification(paid, amount, paymentMethod, paymentLabel, paymentMasked, receiptUrl);
    }

    @Override
    public boolean cancelScheduledPayments(String billingKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApiSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("billingKey", billingKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/payment-schedules",
                HttpMethod.DELETE,
                entity,
                Map.class
        );

        return response.getStatusCode().is2xxSuccessful();
    }
}