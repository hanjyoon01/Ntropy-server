package com.ntropy.account.api.client;

import com.ntropy.account.api.dto.VirtualSettlementDepositCommand;
import com.ntropy.account.api.dto.VirtualSettlementDepositResult;

/** NTROPY 가상계좌에 플랫폼 정산 입금 거래를 멱등하게 생성하는 내부 명령 계약. */
public interface VirtualSettlementDepositCommandClient {

    /** 누적 목표액에서 기존 생성액을 뺀 차액 거래를 만들고, 해당 정산기간의 매칭 가능 여부를 반환한다. */
    VirtualSettlementDepositResult createOrAdjust(VirtualSettlementDepositCommand command);
}
