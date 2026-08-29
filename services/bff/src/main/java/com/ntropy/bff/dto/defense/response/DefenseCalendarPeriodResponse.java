package com.ntropy.bff.dto.defense.response;

import com.ntropy.defense.api.dto.summary.DefenseCalendarPeriodSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class DefenseCalendarPeriodResponse {
    private Long defenseId;
    private LocalDate startDate;
    private LocalDate endDate;

    public static DefenseCalendarPeriodResponse from(DefenseCalendarPeriodSummary summary) {
        return new DefenseCalendarPeriodResponse(
                summary.getDefenseId(), summary.getStartDate(), summary.getEndDate());
    }
}
