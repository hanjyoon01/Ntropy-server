package com.ntropy.account.mapper;

import java.time.LocalDate;
import java.util.List;

import com.ntropy.account.domain.entity.AccountTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountTransactionMapper {

    void insertAll(@Param("list") List<AccountTransaction> transactions);

    List<AccountTransaction> findByAccountIdAndDateRange(@Param("accountId") Long accountId,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    void deleteByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    /**
     * watermark(ACCOUNT_SYNC_STATE)가 아직 없는 기존 연결의 초기 백필 시작점 계산에 쓰인다(이슈 #158).
     * 저장된 거래가 없으면 {@code null}을 반환한다.
     */
    LocalDate findMostRecentTransactionDate(@Param("codefConnectionId") Long codefConnectionId,
                                            @Param("organizationCode") String organizationCode);
}
