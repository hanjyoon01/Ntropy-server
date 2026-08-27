package com.ntropy.account.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ntropy.account.config.IncrementalSyncPolicy;

/**
 * 일일 증분 동기화 조회 시작일을 계산한다 (이슈 #158 섹션 1).
 *
 * <pre>
 * startDate =
 *   max(
 *     최근 저장 거래일 - 안전 중첩 기간,
 *     businessDate - 최대 초기 조회 기간
 *   )
 * 최근 저장 거래가 없으면 businessDate - 최대 초기 조회 기간을 사용한다.
 * </pre>
 *
 * 연결·기관에 watermark({@code last_successful_synced_at})가 이미 있으면 그 값을 기준으로
 * 안전 중첩 구간만큼만 되돌아간다.
 */
public final class IncrementalSyncRangeCalculator {

    private IncrementalSyncRangeCalculator() {
    }

    public static LocalDate startDate(LocalDateTime lastSuccessfulSyncedAt,
                                      LocalDate mostRecentStoredTransactionDate,
                                      LocalDate businessDate,
                                      IncrementalSyncPolicy policy) {
        if (lastSuccessfulSyncedAt != null) {
            return lastSuccessfulSyncedAt.toLocalDate().minus(policy.getSafeOverlapPeriod());
        }

        LocalDate initialLookbackFloor = businessDate.minus(policy.getMaxInitialLookbackPeriod());
        if (mostRecentStoredTransactionDate == null) {
            return initialLookbackFloor;
        }

        LocalDate overlapAdjusted = mostRecentStoredTransactionDate.minus(policy.getSafeOverlapPeriod());
        return overlapAdjusted.isAfter(initialLookbackFloor) ? overlapAdjusted : initialLookbackFloor;
    }
}
