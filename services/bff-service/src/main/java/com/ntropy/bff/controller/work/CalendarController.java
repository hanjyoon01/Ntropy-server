package com.ntropy.bff.controller.work;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.work.api.client.CalendarQueryClient;
import com.ntropy.work.api.dto.summary.CalendarDailySummary;
import com.ntropy.work.api.dto.summary.CalendarMonthlySummary;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "캘린더")
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarQueryClient calendarQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("월간 캘린더 요약 조회")
    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlySummary> getMonthlySummary(@ApiParam(hidden = true) Authentication authentication,
                                                                   @RequestParam int year,
                                                                   @RequestParam int month,
                                                                   @RequestParam(required = false) Double latitude,
                                                                   @RequestParam(required = false) Double longitude) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(calendarQueryClient.getMonthlySummary(userId, year, month, latitude, longitude));
    }

    @ApiOperation("일간 캘린더 요약 조회")
    @GetMapping("/daily")
    public ApiResponse<CalendarDailySummary> getDailySummary(@ApiParam(hidden = true) Authentication authentication,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(required = false) Double latitude,
                                                               @RequestParam(required = false) Double longitude) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(calendarQueryClient.getDailySummary(userId, date, latitude, longitude));
    }
}
