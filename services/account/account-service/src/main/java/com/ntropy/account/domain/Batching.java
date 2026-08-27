package com.ntropy.account.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MyBatis {@code IN}/다중 {@code VALUES} 절에 넘길 리스트를 안전한 크기로 나눈다 (이슈 #233).
 * MySQL packet 크기와 파라미터 수 제한을 고려해 사용자·계좌 수가 많아도 단일 쿼리가 과도하게
 * 커지지 않도록 chunk 단위로 분할한다.
 */
public final class Batching {

    private Batching() {
    }

    public static <T> List<List<T>> chunk(List<T> items, int size) {
        Objects.requireNonNull(items, "items");
        if (size <= 0) {
            throw new IllegalArgumentException("chunk size는 양수여야 합니다.");
        }
        if (items.isEmpty()) {
            return List.of();
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            chunks.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return chunks;
    }
}
