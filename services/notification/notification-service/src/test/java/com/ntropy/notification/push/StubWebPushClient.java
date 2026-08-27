package com.ntropy.notification.push;

import java.util.ArrayList;
import java.util.List;

public class StubWebPushClient implements WebPushClient {

    public final List<String> sentToEndpoints = new ArrayList<>();
    private int nextStatusCode = 201;
    private boolean throwOnSend = false;

    @Override
    public int send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception {
        if (throwOnSend) {
            throw new Exception("웹푸시 전송 실패(테스트용)");
        }
        sentToEndpoints.add(endpoint);
        return nextStatusCode;
    }

    public void willReturnStatus(int statusCode) {
        this.nextStatusCode = statusCode;
    }

    public void willThrow() {
        this.throwOnSend = true;
    }
}
