package com.ntropy.payment.client.portone;

import com.ntropy.payment.config.PortOneProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class PortOneWebhookVerifier {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 5 * 60; // 5분 (포트원 문서에 명시된 허용 오차)
    private static final String SECRET_PREFIX = "whsec_";

    private final PortOneProperties portOneProperties;

    @Autowired
    public PortOneWebhookVerifier(PortOneProperties portOneProperties) {
        this.portOneProperties = portOneProperties;
    }

    public boolean verify(String webhookId, String webhookTimestamp, String webhookSignatureHeader, String rawBody) {
        if (webhookId == null || webhookTimestamp == null || webhookSignatureHeader == null || rawBody == null) {
            return false;
        }
        if (!isTimestampValid(webhookTimestamp)) {
            return false;
        }

        String expectedSignature = computeSignature(webhookId, webhookTimestamp, rawBody);

        for (String candidate : webhookSignatureHeader.trim().split("\\s+")) {
            String[] parts = candidate.split(",", 2);
            if (parts.length == 2 && parts[1].equals(expectedSignature)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTimestampValid(String webhookTimestamp) {
        try {
            long timestampEpochSeconds = Long.parseLong(webhookTimestamp);
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - timestampEpochSeconds) <= TIMESTAMP_TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** package-private: 테스트(같은 패키지)에서 서명 계산 자체만 독립적으로 검증하기 위해 열어둠. */
    String computeSignature(String webhookId, String webhookTimestamp, String rawBody) {
        try {
            String secret = portOneProperties.getWebhookSecret();
            String secretBase64 = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
            byte[] secretBytes = Base64.getDecoder().decode(secretBase64);

            String signedContent = webhookId + "." + webhookTimestamp + "." + rawBody;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] hash = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("웹훅 서명 계산에 실패했습니다.", e);
        }
    }
}