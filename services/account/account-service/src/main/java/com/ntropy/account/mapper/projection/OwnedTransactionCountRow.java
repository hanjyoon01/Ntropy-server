package com.ntropy.account.mapper.projection;

import lombok.Getter;
import lombok.Setter;

/** 소유 계좌 확인과 조건별 거래 건수를 한 번에 반환하는 조회 행. */
@Getter
@Setter
public class OwnedTransactionCountRow {
    private Long ownedAccountId;
    private long totalElements;
}
