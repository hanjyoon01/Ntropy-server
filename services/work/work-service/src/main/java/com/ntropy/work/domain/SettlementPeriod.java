package com.ntropy.work.domain;

import java.time.LocalDate;

/** 정산 대상이 되는 근무 기간 (양 끝 포함). */
public record SettlementPeriod(LocalDate start, LocalDate end) {
}
