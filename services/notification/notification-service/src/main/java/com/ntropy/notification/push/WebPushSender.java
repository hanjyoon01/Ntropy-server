package com.ntropy.notification.push;

import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.notification.domain.entity.PushSubscription;
import com.ntropy.notification.mapper.PushSubscriptionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 유저의 웹푸시 구독 전체에 알림을 전송한다. 외부 푸시 서비스로 나가는 네트워크 호출이라
 * 알림 생성 트랜잭션을 막지 않도록 비동기로 실행한다(NotificationAsyncConfig에서 @EnableAsync).
 * 구독이 만료된 경우(410/404) DB에서 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSender {

    private static final int STATUS_NOT_FOUND = 404;
    private static final int STATUS_GONE = 410;

    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final WebPushClient webPushClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void sendToUser(Long userId, Map<String, Object> payload) {
        List<PushSubscription> subscriptions = pushSubscriptionMapper.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            log.info("웹푸시 구독이 없어 발송을 건너뜁니다: userId={}", userId);
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("웹푸시 payload 직렬화 실패: userId={}", userId, e);
            return;
        }

        log.info("웹푸시 발송 시작: userId={}, 구독수={}", userId, subscriptions.size());
        for (PushSubscription subscription : subscriptions) {
            sendOne(subscription, payloadJson);
        }
    }

    private void sendOne(PushSubscription subscription, String payloadJson) {
        try {
            int statusCode = webPushClient.send(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payloadJson);
            log.info("웹푸시 발송 완료: userId={}, endpoint={}, statusCode={}",
                    subscription.getUserId(), subscription.getEndpoint(), statusCode);
            if (statusCode == STATUS_NOT_FOUND || statusCode == STATUS_GONE) {
                log.info("만료된 웹푸시 구독을 삭제합니다: endpoint={}", subscription.getEndpoint());
                pushSubscriptionMapper.deleteByEndpoint(subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.error("웹푸시 발송 실패: userId={}, endpoint={}", subscription.getUserId(), subscription.getEndpoint(), e);
        }
    }
}
