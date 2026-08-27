package com.ntropy.account.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.mapper.projection.FinancialPositionAccountRow;

/** 재무진단용 금융자산·부채 집계 대상 계좌의 원시 잔액을 조회하는 MyBatis Mapper입니다. */
@Mapper
public interface FinancialPositionMapper {

    List<FinancialPositionAccountRow> findFinancialPositionAccounts(@Param("userId") Long userId);

    /** 기준일 이하 마지막 거래의 거래 후 잔액으로 과거 시점 잔액을 복원한다. */
    List<FinancialPositionAccountRow> findFinancialPositionAccountsAsOf(
            @Param("userId") Long userId,
            @Param("asOf") LocalDate asOf
    );
}
