package com.ntropy.work.port.account;

import java.time.LocalDate;
import java.util.List;

/** work-service가 정의한, account-service의 입금 거래 조회 포트. */
public interface IncomingTransactionPort {

    List<IncomingTransaction> findIncomingTransactions(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
