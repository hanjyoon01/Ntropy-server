package com.ntropy.work.api.client;

import java.time.LocalDate;

import com.ntropy.work.api.dto.summary.CalendarDailySummary;
import com.ntropy.work.api.dto.summary.CalendarMonthlySummary;

/**
 * work-service의 캘린더(WORK_LOG 집계) 조회 계약. work-service가 LocalCalendarQueryClient로
 * 구현하고, bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 * latitude/longitude는 날씨 조회용 — null이면 기본 좌표(서울시청)를 쓴다.
 */
public interface CalendarQueryClient {

    CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month, Double latitude, Double longitude);

    CalendarDailySummary getDailySummary(Long userId, LocalDate date, Double latitude, Double longitude);
}
