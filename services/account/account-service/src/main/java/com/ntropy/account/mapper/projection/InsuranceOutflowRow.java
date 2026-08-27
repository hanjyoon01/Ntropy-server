package com.ntropy.account.mapper.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 보험료 반복 감지 대상이 되는 수시입출 계좌의 출금 거래 1건. */
@Getter
@Setter
public class InsuranceOutflowRow {
    private Long accountId;
    private LocalDate tranDate;
    private BigDecimal outAmount;
    private String desc1;
    private String desc2;
    private String desc3;
    private String desc4;
}
