package com.ntropy.work.api.client;

import java.time.LocalDate;

/** 가상 정산 입금 생성과 후속 정산 매칭을 사용자·날짜 단위로 수동 실행하기 위한 테스트 계약. */
public interface VirtualSettlementDepositBatchCommandClient {

    /** 특정 사용자의 도래한 가상 입금을 생성하고, 생성된 입금일의 정산 매칭까지 즉시 실행한다. */
    BatchResult runForDate(Long userId, LocalDate processDate);

    /** 수동 배치 한 번의 실행 결과. 실제 금액이나 계좌 정보는 노출하지 않고 건수만 반환한다. */
    record BatchResult(int createdDepositCount, int matchedSettlementCount) {
    }
}
