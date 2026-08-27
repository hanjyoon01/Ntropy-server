package com.ntropy.account.config;

import java.time.Period;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * 일일 증분 동기화의 안전 중첩 구간·초기 백필 범위 정책 (이슈 #158).
 * 코드에 흩어진 리터럴이 아니라 이 설정으로 관리한다.
 */
@Getter
@Component
public class IncrementalSyncPolicy {

    private final Period safeOverlapPeriod;
    private final Period maxInitialLookbackPeriod;

    public IncrementalSyncPolicy(
            @Value("${daily-sync.safe-overlap-days:1}") long safeOverlapDays,
            @Value("${daily-sync.max-initial-lookback-days:90}") long maxInitialLookbackDays
    ) {
        if (safeOverlapDays <= 0) {
            throw new IllegalStateException("daily-sync.safe-overlap-days는 양수여야 합니다");
        }
        if (maxInitialLookbackDays <= 0) {
            throw new IllegalStateException("daily-sync.max-initial-lookback-days는 양수여야 합니다");
        }
        this.safeOverlapPeriod = Period.ofDays((int) safeOverlapDays);
        this.maxInitialLookbackPeriod = Period.ofDays((int) maxInitialLookbackDays);
    }
}
