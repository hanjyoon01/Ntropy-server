package com.ntropy.account.api.client;

import com.ntropy.account.api.dto.FinancialCommitmentSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * account-service가 구현해야 하는 방어모드용 금융 납입 예정 목록 조회 계약.
 *
 * <p>CODEF 적금·대출 거래내역과 보험 관련 출금내역을 분석하여 적금 납입,
 * 대출 상환, 보험료 항목을 공통 형식으로 반환한다. 직접 제공된 값은
 * CONFIRMED, 거래내역으로 추정한 값은 ESTIMATED, 산출할 수 없는 값은
 * null과 INSUFFICIENT로 반환한다.</p>
 *
 * <p>대출 예상 상환액은 최근 실제 상환 거래의 거래금액 또는 원금+이자를
 * 이용해 추정하며, 신규·실행·증액 거래는 상환액 계산에서 제외한다.</p>
 */
public interface FinancialCommitmentQueryClient {

    /**
     * 조회 기간에 납입 예정인 활성 금융상품을 반환한다.
     * 납입일을 알 수 없는 활성 대출·적금도 목록에서 제외하지 않는다.
     * 보험은 실제 거래 설명의 상품명별로 분리하고 최신 출금일과 금액으로 다음 납입을 추정한다.
     */
    List<FinancialCommitmentSummary> findFinancialCommitments(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate);
}
