package com.ntropy.user.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VirtualTestPropertiesTest {

    @Test
    void enabledWithoutExplicitLocalOrTestEnvironmentFailsClosed() {
        assertThrows(IllegalStateException.class, () -> properties(true, 50, "unknown"));
        assertThrows(IllegalStateException.class, () -> properties(true, 50, "production"));
    }

    @Test
    void enabledAcceptsOnlyNormalizedLocalOrTestEnvironment() {
        VirtualTestProperties local = properties(true, 50, " LOCAL ");
        VirtualTestProperties test = properties(true, 50, "test");

        assertTrue(local.isEnabled());
        assertEquals("local", local.getDeploymentEnvironment());
        assertEquals("test", test.getDeploymentEnvironment());
    }

    @Test
    void disabledAllowsUnknownEnvironment() {
        VirtualTestProperties properties = properties(false, 50, "unknown");

        assertFalse(properties.isEnabled());
        assertEquals("unknown", properties.getDeploymentEnvironment());
    }

    @Test
    void rejectsUserCountOutsideProviderIdRange() {
        assertThrows(IllegalArgumentException.class, () -> properties(false, 0, "unknown"));
        assertThrows(IllegalArgumentException.class, () -> properties(false, 1_000_000, "unknown"));
    }

    private static VirtualTestProperties properties(boolean enabled, int userCount, String environment) {
        return new VirtualTestProperties(
                enabled, userCount, 20260817L, "2026-08-17", "VIRTUAL-MULTI-DOMAIN-v1", environment
        );
    }
}
