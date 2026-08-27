package com.ntropy.notification.push;

import java.nio.charset.StandardCharsets;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

/** web-push 라이브러리(nl.martijndwars:web-push)를 실제로 호출하는 WebPushClient 구현체. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushServiceClient implements WebPushClient {

    private final PushService pushService;

    @Override
    public int send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception {
        Subscription subscription = new Subscription(endpoint, new Subscription.Keys(p256dh, auth));
        Notification notification = new Notification(subscription, payloadJson);
        // 인자 없는 send()는 레거시 aesgcm으로 보내는데, 그 경로가 만드는 Crypto-Key 헤더를 FCM이
        // "invalid format"으로 거부한다(403). aes128gcm(RFC 8291)은 그 헤더 없이 Authorization의
        // vapid t=/k= 만 쓴다.
        HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
        int statusCode = response.getStatusLine().getStatusCode();
        // 푸시 서비스는 거부 사유(VAPID 서명 오류, sender 불일치 등)를 상태코드가 아니라
        // 응답 본문에 담아준다 — 4xx일 때 본문 없이는 원인 구분이 불가능하다.
        if (statusCode >= 400 && response.getEntity() != null) {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.warn("웹푸시 거부 응답: statusCode={}, endpoint={}, body={}", statusCode, endpoint, body);
        }
        return statusCode;
    }
}
