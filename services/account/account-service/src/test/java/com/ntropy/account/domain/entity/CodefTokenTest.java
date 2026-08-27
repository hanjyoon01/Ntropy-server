package com.ntropy.account.domain.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class CodefTokenTest {

    @Test
    void treatsTokenInsideRefreshSkewAsUnusable() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        CodefToken token = new CodefToken();
        token.setExpiresAt(now.plusMinutes(30));

        assertFalse(token.isExpired(now));
        assertFalse(token.isUsableAt(now, Duration.ofHours(1)));
    }

    @Test
    void acceptsTokenOutsideRefreshSkew() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 12, 0);
        CodefToken token = new CodefToken();
        token.setExpiresAt(now.plusHours(2));

        assertTrue(token.isUsableAt(now, Duration.ofHours(1)));
    }
}
