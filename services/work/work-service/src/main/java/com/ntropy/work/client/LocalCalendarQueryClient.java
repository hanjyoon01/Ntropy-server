package com.ntropy.work.client;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.CalendarQueryClient;
import com.ntropy.work.api.dto.summary.CalendarDailySummary;
import com.ntropy.work.api.dto.summary.CalendarMonthlySummary;
import com.ntropy.work.service.CalendarService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalCalendarQueryClient implements CalendarQueryClient {

    private final CalendarService calendarService;

    @Override
    public CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month, Double latitude, Double longitude) {
        return calendarService.getMonthlySummary(userId, year, month, latitude, longitude);
    }

    @Override
    public CalendarDailySummary getDailySummary(Long userId, LocalDate date, Double latitude, Double longitude) {
        return calendarService.getDailySummary(userId, date, latitude, longitude);
    }
}
