package com.ntropy.work.port.account;

/** work-service가 정의한, account-service의 가상 정산 입금 명령 포트. */
public interface SettlementDepositPort {

    /** 누적 목표액에서 기존 생성액을 뺀 차액 거래를 만들고, 해당 정산기간의 매칭 가능 여부를 반환한다. */
    SettlementDepositOutcome createOrAdjust(SettlementDepositRequest request);
}
