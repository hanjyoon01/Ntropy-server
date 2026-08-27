package com.ntropy.work.domain.enums;

/**
 * SETTLEMENT 행이 입금 거래-잡 매칭에 성공했는지 여부.
 * AMBIGUOUS(한 플랫폼에 잡 여러 개 매핑)는 발생하지 않는다고 가정하고 다루지 않는다.
 */
public enum SettlementMatchStatus {
    MATCHED,
    UNMATCHED
}
