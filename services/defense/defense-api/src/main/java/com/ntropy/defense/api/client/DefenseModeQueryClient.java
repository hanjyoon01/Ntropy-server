package com.ntropy.defense.api.client;

import com.ntropy.defense.api.dto.summary.DefenseModeSummary;
import com.ntropy.defense.api.dto.summary.DefenseCauseSummary;
import com.ntropy.defense.api.dto.summary.DefenseCalendarPeriodSummary;

import java.time.LocalDate;
import java.util.List;

public interface DefenseModeQueryClient {
    List<DefenseCauseSummary> getCauses();
    DefenseModeSummary getCurrent(Long userId);
    List<DefenseCalendarPeriodSummary> getCalendarPeriods(Long userId, LocalDate from, LocalDate to);
}
