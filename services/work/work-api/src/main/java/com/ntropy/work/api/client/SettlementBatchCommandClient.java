package com.ntropy.work.api.client;

import java.time.LocalDate;

/**
 * 정산 배치(SettlementService)를 사용자·날짜 단위로 수동 실행하기 위한 계약.
 * 원래는 SettlementScheduler가 매일 자동으로만 실행하는데, 테스트/디버깅 목적으로
 * 특정 사용자·날짜 하나만 즉시 돌려보고 싶을 때 이 인터페이스를 통해 work-service의
 * SettlementService.processSettlement()를 그대로 호출한다.
 */
public interface SettlementBatchCommandClient {

    /** processDate 하루치 입금 거래를 매칭해 SETTLEMENT를 생성한다. 새로 생성됐으면 true. */
    boolean runForDate(Long userId, LocalDate processDate);
}
