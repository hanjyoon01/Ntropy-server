package com.ntropy.work.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ntropy.work.domain.enums.SettlementMatchStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    private Long settlementId;
    private Long userId;
    private SettlementMatchStatus status;
    private Long jobId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate depositDate;
    private Long expectedAmount;
    private Long actualAmount;
    private Integer transactionCount;
    private Long accountTransactionId;
    private LocalDateTime matchedAt;
}
