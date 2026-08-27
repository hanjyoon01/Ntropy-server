package com.ntropy.work.domain;

import java.time.LocalDate;
import java.util.List;

/** 가상 입금 생성 결과와 기존 정산 배치가 즉시 재확인할 과거 입금일 목록. */
public record VirtualSettlementDepositBatchResult(
        int createdCount,
        List<MatchTarget> matchTargets
) {

    public VirtualSettlementDepositBatchResult {
        matchTargets = matchTargets == null ? List.of() : List.copyOf(matchTargets);
    }

    public static VirtualSettlementDepositBatchResult empty() {
        return new VirtualSettlementDepositBatchResult(0, List.of());
    }

    public record MatchTarget(Long userId, LocalDate depositDate) {
    }
}
