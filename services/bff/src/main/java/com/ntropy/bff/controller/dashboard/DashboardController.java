package com.ntropy.bff.controller.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.dashboard.response.DashboardHoursResponse;
import com.ntropy.bff.dto.dashboard.response.DashboardIncomeResponse;
import com.ntropy.bff.dto.dashboard.response.DashboardResponse;
import com.ntropy.bff.dto.dashboard.response.JobRecommendationsResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.api.client.UserQueryClient;
import com.ntropy.user.api.dto.UserSummary;
import com.ntropy.work.api.client.CalendarQueryClient;
import com.ntropy.work.api.client.IncomeAnalysisQueryClient;
import com.ntropy.work.api.client.RecommendedWorkHoursQueryClient;
import com.ntropy.work.api.dto.summary.CalendarDailySummary;
import com.ntropy.work.api.dto.summary.CalendarMonthlyHours;
import com.ntropy.work.api.dto.summary.CalendarMonthlySummary;
import com.ntropy.work.api.dto.summary.JobFatigueSummary;
import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserQueryClient userQueryClient;
    private final CalendarQueryClient calendarQueryClient;
    private final IncomeAnalysisQueryClient incomeAnalysisQueryClient;
    private final RecommendedWorkHoursQueryClient recommendedWorkHoursQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("홈 대시보드 조회")
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);

        UserSummary user = userQueryClient.getUserSummary(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        CalendarMonthlySummary monthlySummary = calendarQueryClient.getMonthlySummary(
                userId, thisMonth.getYear(), thisMonth.getMonthValue(), null, null);
        CalendarMonthlyHours hours = monthlySummary.getSummary();

        MonthlyIncomeAnalysisSummary incomeAnalysis =
                incomeAnalysisQueryClient.getMonthlyIncomeAnalysis(userId, thisMonth);

        CalendarDailySummary dailySummary = calendarQueryClient.getDailySummary(userId, today, null, null);
        Integer fatigueScore = dailySummary.getFatigue() == null ? null : dailySummary.getFatigue().getScore();

        DashboardResponse response = new DashboardResponse(
                user.name(),
                new DashboardHoursResponse(hours.getConfirmedHours(), hours.getScheduledHours(), hours.getGoalHours()),
                new DashboardIncomeResponse(incomeAnalysis.getTotalIncome(), hours.getExpectedSettlementIncome(), hours.getTargetAmount()),
                fatigueScore
        );
        return ApiResponse.success(response);
    }

    @ApiOperation("잡별 추천 근무시간 조회")
    @GetMapping("/recommendation-hours")
    public ApiResponse<JobRecommendationsResponse> getRecommendationHours(
            @ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        YearMonth thisMonth = YearMonth.now();

        MonthlyIncomeAnalysisSummary incomeAnalysis =
                incomeAnalysisQueryClient.getMonthlyIncomeAnalysis(userId, thisMonth);
        Map<Long, Long> currentHoursByJobId = incomeAnalysis.getFatigueSummaries().stream()
                .collect(Collectors.toMap(JobFatigueSummary::getJobId, s -> s.getTotalWorkMinutes() / 60));

        JobRecommendationsResponse response = JobRecommendationsResponse.from(
                recommendedWorkHoursQueryClient.getCurrentMonthRecommendedWorkHours(userId),
                currentHoursByJobId);
        return ApiResponse.success(response);
    }
}
