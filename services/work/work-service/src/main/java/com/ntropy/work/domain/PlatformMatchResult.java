package com.ntropy.work.domain;

import java.util.List;

import com.ntropy.work.domain.entity.Platform;

/**
 * 정규화된 거래명과 PLATFORM 목록 대조 결과.
 */
public sealed interface PlatformMatchResult {

    record Matched(Platform platform) implements PlatformMatchResult {
    }

    record NotMatched() implements PlatformMatchResult {
    }

    record Ambiguous(List<Platform> candidates) implements PlatformMatchResult {
    }
}
