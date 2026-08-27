package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VirtualConnectedIdTest {

    @Test
    void generatesIdWithNtropyPrefix() {
        String connectedId = VirtualConnectedId.generate();

        assertTrue(connectedId.startsWith("NTROPY-"));
    }

    @Test
    void generatesUniqueIdsOnEachCall() {
        String first = VirtualConnectedId.generate();
        String second = VirtualConnectedId.generate();

        assertNotEquals(first, second);
    }
}
