package com.ntropy.account.domain;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * {@code DAILY_BATCH_EXECUTION.owner_id}로 쓸 실행 인스턴스 식별자를 만든다.
 * 호스트명:PID만으로는 크래시 직후 같은 PID가 재사용되는 드문 경우를 구분할 수 없어
 * 짧은 랜덤 접미사를 덧붙인다.
 */
public final class InstanceOwnerId {

    private InstanceOwnerId() {
    }

    public static String generate() {
        return hostname() + ":" + pid() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private static String pid() {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int at = jvmName.indexOf('@');
        return at > 0 ? jvmName.substring(0, at) : jvmName;
    }
}
