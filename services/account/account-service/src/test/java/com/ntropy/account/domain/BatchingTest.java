package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class BatchingTest {

    @Test
    void returnsEmptyChunksForEmptyInput() {
        assertEquals(List.of(), Batching.chunk(List.of(), 3));
    }

    @Test
    void preservesOrderAcrossChunks() {
        assertEquals(
                List.of(List.of(1, 2), List.of(3, 4), List.of(5)),
                Batching.chunk(List.of(1, 2, 3, 4, 5), 2)
        );
    }

    @Test
    void keepsExactSizeInputInOneChunk() {
        assertEquals(List.of(List.of(1, 2, 3)), Batching.chunk(List.of(1, 2, 3), 3));
    }

    @Test
    void rejectsNonPositiveChunkSize() {
        assertThrows(IllegalArgumentException.class, () -> Batching.chunk(List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> Batching.chunk(List.of(1), -1));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> Batching.chunk(null, 1));
    }
}
