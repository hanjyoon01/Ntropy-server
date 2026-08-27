package com.ntropy.bff.dto.defense.response;

import com.ntropy.defense.api.dto.summary.DefenseCalendarPeriodSummary;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class DefenseCalendarResponse {
    private final List<DefenseCalendarPeriodResponse> periods;

    public DefenseCalendarResponse(List<DefenseCalendarPeriodSummary> periods) {
        this.periods = periods.stream()
                .map(DefenseCalendarPeriodResponse::from)
                .collect(Collectors.toList());
    }
}
