package com.ntropy.account.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void usesAsiaSeoulAsServiceTimeZone() {
        assertEquals(ZoneId.of("Asia/Seoul"), new ClockConfig().clock().getZone());
    }
}
