package com.ntropy.account.domain;

import java.util.UUID;

/**
 * 가상(NTROPY) 연결용 connectedId를 발급한다.
 * 실제 CODEF connectedId와 형식으로 구분되도록 접두사를 붙이며, 실제 CODEF API에는 전달하지 않는다.
 */
public final class VirtualConnectedId {

    private static final String PREFIX = "NTROPY-";

    private VirtualConnectedId() {
    }

    public static String generate() {
        return PREFIX + UUID.randomUUID();
    }
}
