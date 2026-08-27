package com.ntropy.diagnosis.port.account;

import java.time.LocalDate;

/** diagnosis-service가 정의한, account-service의 금융자산 조회 포트. */
public interface FinancialPositionPort {

    /** 현재(조회 시점) 잔액 기준 금융자산. */
    FinancialPosition findFinancialPosition(Long userId);

    /** {@code asOf} 기준일 이하 마지막 거래의 거래 후 잔액으로 복원한 과거 시점 금융자산. */
    FinancialPosition findFinancialPosition(Long userId, LocalDate asOf);
}
