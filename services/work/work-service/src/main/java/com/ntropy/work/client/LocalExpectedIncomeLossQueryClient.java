package com.ntropy.work.client;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.ExpectedIncomeLossQueryClient;
import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.service.JobService;

import lombok.RequiredArgsConstructor;

/**
 * 방어모드 기간 동안 근무하지 못해 발생할 예상 손실소득을 잡별로 계산한다.
 *
 * <p>잡 등록/수정 시점에 저장해둔 월 환산 예상 소득(monthly_expected_income)을
 * 방어기간 일수만큼 일할 계산한다(30일 기준). MONTHLY와 정기(스케줄 있는) HOURLY 잡은
 * 등록/수정 시점에 이 값을 계산할 근거(고정 월급 또는 고정 스케줄×시급)가 있어 항상
 * 채워져 있다. 반면 PER_TASK와 비정기(스케줄 없는) HOURLY 잡은 등록 시점에 확정할
 * 근거가 없어 monthly_expected_income이 항상 null이므로, 대신 조회 시점 기준 최근
 * 3개월(이번 달 제외, 완료된 달만) MATCHED 정산 실적을 데이터가 있는 달만으로 평균
 * 내 사용한다. 최근 3개월 정산 이력이 전혀 없으면 다른 잡과 동일하게 손실액을
 * null(계산 불가)로 반환한다.</p>
 */
@Component
@RequiredArgsConstructor
public class LocalExpectedIncomeLossQueryClient implements ExpectedIncomeLossQueryClient {

    private static final int DAYS_PER_MONTH = 30;
    private static final int RECENT_MONTHS_FOR_AVERAGE = 3;

    private final JobService jobService;
    private final SettlementMapper settlementMapper;

    @Override
    public List<JobExpectedIncomeLossSummary> findExpectedIncomeLossByJob(
            Long userId, LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        List<Job> activeJobs = jobService.findByUserId(userId).stream()
                .filter(job -> Boolean.TRUE.equals(job.getIsActive()))
                .collect(Collectors.toList());

        // monthly_expected_income이 등록 시점에 계산되지 않은 잡(PER_TASK, 비정기 HOURLY 등)만
        // 최근 실적 평균 대상이 된다.
        List<Job> jobsWithoutSnapshot = activeJobs.stream()
                .filter(job -> job.getMonthlyExpectedIncome() == null)
                .collect(Collectors.toList());
        Map<Long, Long> recentAverageIncomeByJob = calculateRecentAverageIncomeByJob(jobsWithoutSnapshot);

        return activeJobs.stream()
                .map(job -> toSummary(job, days, recentAverageIncomeByJob))
                .collect(Collectors.toList());
    }

    private JobExpectedIncomeLossSummary toSummary(Job job, long days, Map<Long, Long> recentAverageIncomeByJob) {
        Long monthlyIncome = job.getMonthlyExpectedIncome() != null
                ? job.getMonthlyExpectedIncome()
                : recentAverageIncomeByJob.get(job.getJobId());
        return new JobExpectedIncomeLossSummary(
                job.getJobId(),
                job.getJobName(),
                calculateLoss(monthlyIncome, days));
    }

    private Long calculateLoss(Long monthlyExpectedIncome, long days) {
        if (monthlyExpectedIncome == null) {
            return null;
        }
        return Math.round(monthlyExpectedIncome * ((double) days / DAYS_PER_MONTH));
    }

    /**
     * monthly_expected_income이 없는 잡들의 최근 3개월(이번 달 제외) 월 평균 실적
     * 소득을 잡별로 계산한다. 데이터가 있는 달만으로 평균을 내며(예: 1개월치만
     * 있으면 그 1개월로 평균), 최근 3개월 내 MATCHED 정산이 전혀 없는 잡은 결과
     * 맵에 포함되지 않는다(호출부에서 get() 시 null → 계산 불가로 처리됨).
     */
    private Map<Long, Long> calculateRecentAverageIncomeByJob(List<Job> jobsWithoutSnapshot) {
        if (jobsWithoutSnapshot.isEmpty()) {
            return Map.of();
        }

        List<Long> jobIds = jobsWithoutSnapshot.stream().map(Job::getJobId).collect(Collectors.toList());
        YearMonth currentMonth = YearMonth.now();
        LocalDate startDate = currentMonth.minusMonths(RECENT_MONTHS_FOR_AVERAGE).atDay(1);
        LocalDate endDate = currentMonth.minusMonths(1).atEndOfMonth();

        List<Settlement> settlements = settlementMapper.findByJobIdInAndDepositDateRangeAndStatus(
                jobIds, startDate, endDate, SettlementMatchStatus.MATCHED);

        Map<Long, Map<YearMonth, Long>> monthlySumsByJob = new HashMap<>();
        for (Settlement settlement : settlements) {
            monthlySumsByJob
                    .computeIfAbsent(settlement.getJobId(), key -> new HashMap<>())
                    .merge(YearMonth.from(settlement.getDepositDate()), settlement.getActualAmount(), Long::sum);
        }

        Map<Long, Long> result = new HashMap<>();
        for (Map.Entry<Long, Map<YearMonth, Long>> entry : monthlySumsByJob.entrySet()) {
            Map<YearMonth, Long> monthlySums = entry.getValue();
            long total = monthlySums.values().stream().mapToLong(Long::longValue).sum();
            result.put(entry.getKey(), Math.round((double) total / monthlySums.size()));
        }
        return result;
    }
}
