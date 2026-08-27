package com.ntropy.account.api.dto;

/** 가상 정산 입금 명령 결과. available이면 해당 정산기간을 실제 입금 조회로 매칭할 수 있다. */
public record VirtualSettlementDepositResult(
        boolean available,
        boolean transactionCreated
) {

    public static VirtualSettlementDepositResult unavailable() {
        return new VirtualSettlementDepositResult(false, false);
    }

    public static VirtualSettlementDepositResult alreadyAvailable() {
        return new VirtualSettlementDepositResult(true, false);
    }

    public static VirtualSettlementDepositResult created() {
        return new VirtualSettlementDepositResult(true, true);
    }
}
