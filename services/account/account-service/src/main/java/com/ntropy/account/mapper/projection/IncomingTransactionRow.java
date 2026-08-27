package com.ntropy.account.mapper.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 입금 거래 조회와 은행별 상대방명 추출에 필요한 account-service 내부 Projection. */
@Getter
@Setter
@NoArgsConstructor
public class IncomingTransactionRow {

    private Long transactionId;
    private String organizationCode;
    private LocalDate transactionDate;
    private LocalTime transactionTime;
    private BigDecimal amount;
    private String desc1;
    private String desc2;
    private String desc3;
    private String desc4;
}
