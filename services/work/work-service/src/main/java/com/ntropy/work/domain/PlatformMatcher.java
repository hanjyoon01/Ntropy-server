package com.ntropy.work.domain;

import java.util.List;

import com.ntropy.work.domain.entity.Platform;

/**
 * 입금 거래명(counterpartyName)을 PLATFORM.deposit_name과 대조해 platform을 식별한다.
 * 온보딩 잡 후보 추출, 정산 매칭 기능이 공통으로 사용하는 공용 매칭 로직.
 */
public final class PlatformMatcher {

    private PlatformMatcher() {
    }

    public static PlatformMatchResult match(String counterpartyName, List<Platform> platforms) {
        String normalized = PlatformNameNormalizer.normalize(counterpartyName);
        List<Platform> matched = platforms.stream()
                .filter(platform -> PlatformNameNormalizer.normalize(platform.getDepositName()).equals(normalized))
                .toList();

        if (matched.isEmpty()) {
            return new PlatformMatchResult.NotMatched();
        }
        if (matched.size() == 1) {
            return new PlatformMatchResult.Matched(matched.get(0));
        }
        return new PlatformMatchResult.Ambiguous(matched);
    }
}
