package com.ntropy.defense.port.account;

import java.time.LocalDate;
import java.util.List;

/** defense-service가 정의한, account-service의 금융 납입 예정 목록 조회 포트. */
@FunctionalInterface
public interface FinancialCommitmentPort {

    List<FinancialCommitment> findFinancialCommitments(Long userId, LocalDate fromDate, LocalDate toDate);
}
