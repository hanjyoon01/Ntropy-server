package com.ntropy.notification.push;

/**
 * 실제 웹푸시 전송을 감싸는 얇은 인터페이스. WebPushSender가 이 인터페이스에만 의존하게 해서
 * 테스트에서 실제 네트워크 호출(외부 푸시 서비스) 없이 페이크로 검증할 수 있게 한다.
 */
public interface WebPushClient {

    /** 전송 결과 HTTP 상태 코드를 반환한다 (410/404면 구독이 만료된 것으로 간주). */
    int send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception;
}
