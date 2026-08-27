package com.ntropy.account.api.client;

import java.time.LocalDate;
import java.util.List;

import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

/**
 * provider별 일일 금융거래 증분 동기화 명령 계약 (이슈 #158).
 * "활성 사용자" 목록은 이 계약의 구현체가 스스로 정의하지 않고 호출자가 activeUserIds로 전달한다.
 */
public interface DailyFinancialSyncClient {

    DailyFinancialSyncResult synchronize(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                          LocalDate businessDate);
}
