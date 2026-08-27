package com.ntropy.account.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.entity.AccountTransaction;

/** NTROPY 가상 정산 거래의 멱등 저장과 계좌 잔액 반영 전용 Mapper. */
@Mapper
public interface VirtualSettlementDepositMapper {

    BigDecimal findBalanceForUpdate(@Param("accountId") Long accountId);

    BigDecimal sumGeneratedAmount(
            @Param("accountId") Long accountId,
            @Param("settlementKey") String settlementKey);

    int insertIfAbsent(AccountTransaction transaction);

    int incrementBalanceAndAdvanceLastTranDate(
            @Param("accountId") Long accountId,
            @Param("amount") BigDecimal amount,
            @Param("tranDate") LocalDate tranDate);
}
