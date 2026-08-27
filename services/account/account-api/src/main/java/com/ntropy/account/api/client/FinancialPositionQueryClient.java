package com.ntropy.account.api.client;

import java.time.LocalDate;

import com.ntropy.account.api.dto.FinancialPositionSummary;

/**
 * diagnosis-service가 재무진단에 사용하는 사용자 금융자산·부채 집계 조회 계약.
 *
 * <p>account-service에 이미 동기화된 ACCOUNT 데이터만으로 계산하며 CODEF API를
 * 다시 호출하지 않는다. 집계 대상 계좌의 잔액이 불완전하면(소수부, null, 음수,
 * Long 변환·합산 overflow) 예외를 던져 호출자가 진단 결과 갱신을 중단하도록 한다.</p>
 */
public interface FinancialPositionQueryClient {

    /** 현재(조회 시점) 잔액 기준 금융자산·부채. */
    FinancialPositionSummary findFinancialPosition(Long userId);

    /**
     * {@code asOf} 기준일 이하 마지막 거래의 거래 후 잔액으로 복원한 과거 시점 금융자산·부채.
     *
     * <p>기준일 이하 거래가 없거나 마지막 거래의 거래 후 잔액이 없는 계좌는 집계에서
     * 제외한다. 계좌 분류(예적금/대출 등)는 이력이 없어 현재 값을 그대로 적용한다.</p>
     */
    FinancialPositionSummary findFinancialPosition(Long userId, LocalDate asOf);
}
