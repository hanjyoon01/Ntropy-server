package com.ntropy.account.service;

import java.time.LocalDate;
import java.time.YearMonth;

/** 적금 납입일과 대출 상환일에 실제 값이 없을 때 적용하는 기본 일정 정책. */
final class DefaultPaymentSchedule {

    static final int DAY_OF_MONTH = 25;

    private DefaultPaymentSchedule() {
    }

    static LocalDate occurrenceIn(YearMonth month) {
        return month.atDay(DAY_OF_MONTH);
    }

    /** 기준일 이후 가장 가까운 기본 납입·상환일을 반환한다. */
    static LocalDate nextAfter(LocalDate referenceDate) {
        YearMonth currentMonth = YearMonth.from(referenceDate);
        LocalDate candidate = occurrenceIn(currentMonth);
        return candidate.isAfter(referenceDate)
                ? candidate
                : occurrenceIn(currentMonth.plusMonths(1));
    }
}
