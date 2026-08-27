package com.ntropy.account.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 실제 CODEF 금융 연결 상태. connectedId 원문은 외부에 노출하지 않는다. */
public record MyDataConnectionSummary(
        boolean connected,
        List<BankSummary> connectedBanks,
        LocalDateTime updatedAt
) {
}
