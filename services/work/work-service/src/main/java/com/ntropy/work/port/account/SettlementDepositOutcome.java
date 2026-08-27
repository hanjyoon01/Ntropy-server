package com.ntropy.work.port.account;

/** 가상 정산 입금 요청 결과. available이면 해당 정산기간을 실제 입금 조회로 매칭할 수 있다. */
public record SettlementDepositOutcome(
        boolean available,
        boolean transactionCreated
) {
}
