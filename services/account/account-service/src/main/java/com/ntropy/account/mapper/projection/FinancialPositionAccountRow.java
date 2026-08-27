package com.ntropy.account.mapper.projection;

import java.math.BigDecimal;

import com.ntropy.account.domain.AccountGroup;

import lombok.Getter;
import lombok.Setter;

/** 금융자산·부채 집계 대상 계좌 1건의 원시 잔액. 소수부·null·음수 검증은 Service에서 수행한다. */
@Getter
@Setter
public class FinancialPositionAccountRow {
    private Long accountId;
    private String depositTypeCode;
    private AccountGroup accountGroup;
    private Boolean overdraftYn;
    private BigDecimal balance;
}
