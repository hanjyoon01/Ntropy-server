package com.ntropy.work.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.work.api.dto.summary.JobSummary;
import com.ntropy.work.api.dto.summary.RecommendedJobHoursSummary;
import com.ntropy.work.api.dto.summary.RecommendedWorkHoursSummary;
import com.ntropy.work.domain.entity.AllocationGoal;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.mapper.AllocationGoalMapper;
import com.ntropy.work.mapper.JobMapper;

import lombok.RequiredArgsConstructor;

/**
 * 목표 금액을 활성 비정기 잡에 배분하는 결정적 계산 서비스입니다.
 * 같은 입력이면 같은 결과가 나오도록 수치 계산에는 LLM/FastAPI를 사용하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class RecommendedWorkHoursService {

    private static final String MONTHLY = "MONTHLY";
    private static final String HOURLY = "HOURLY";
    private static final String PER_TASK = "PER_TASK";

    /** 희망 노동 강도별 월 추가 근무시간 상한입니다(주당 정책값 x 4주). */
    private static final Map<Long, Long> MONTHLY_HOUR_LIMITS = Map.of(
            1L, 32L, 2L, 48L, 3L, 64L, 4L, 80L, 5L, 96L);

    /** 피로도가 높을수록 추천 우선순위를 낮추는 가중치입니다. */
    private static final Map<Integer, Double> FATIGUE_WEIGHTS = Map.of(
            1, 1.00, 2, 0.85, 3, 0.70, 4, 0.55, 5, 0.40);

    /** 특정 잡에 시간이 몰리지 않도록 적용하는 최대 비중입니다. */
    private static final Map<Integer, Double> FATIGUE_TIME_SHARES = Map.of(
            1, 1.00, 2, 0.90, 3, 0.80, 4, 0.70, 5, 0.60);

    private final SavingGoalService savingGoalService;
    private final JobMapper jobMapper;
    private final AllocationGoalMapper allocationGoalMapper;

    @Transactional
    public RecommendedWorkHoursSummary getCurrentMonthRecommendedWorkHours(Long userId) {
        SavingGoal goal = savingGoalService.findCurrentMonthGoal(userId);
        if (goal == null) {
            return null;
        }

        String targetMonth = goal.getTargetMonth();
        List<Job> jobs = jobMapper.findByUserId(userId).stream()
                .filter(job -> Boolean.TRUE.equals(job.getIsActive()))
                .toList();
        List<JobSummary> summaries = jobs.stream().map(this::toSummary).toList();

        // 이미 계산된 이번 달 결과가 있으면 재계산하지 않고 캐시처럼 재사용합니다.
        List<AllocationGoal> saved = allocationGoalMapper.findByUserIdAndTargetMonth(userId, targetMonth);
        if (!saved.isEmpty()) {
            return toResponse(targetMonth, saved, summaries);
        }

        return calculateAndStore(userId, targetMonth, goal.getTargetAmount(),
                goal.getLaborIntensity(), summaries);
    }

    private RecommendedWorkHoursSummary calculateAndStore(
            Long userId, String targetMonth, Long targetAmount, Long laborIntensity,
            List<JobSummary> jobs) {
        long fixedMonthlyIncome = jobs.stream()
                .filter(this::isRegularMonthlyJob)
                .mapToLong(job -> job.getMonthlyWage().longValue())
                .sum();
        long allocationTarget = Math.max(0L, targetAmount - fixedMonthlyIncome);
        long monthlyLimit = MONTHLY_HOUR_LIMITS.getOrDefault(laborIntensity, 0L);

        List<Candidate> candidates = jobs.stream()
                .filter(job -> !isRegularMonthlyJob(job))
                .map(this::toCandidate)
                .filter(candidate -> candidate != null)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparingInt(candidate -> candidate.job().getBaseFatigue())
                        .thenComparing(Comparator.comparingDouble(Candidate::hourlyIncome).reversed())
                        .thenComparing(candidate -> candidate.job().getJobId()))
                .toList();

        List<AllocationGoal> allocations = new ArrayList<>();
        long remainingAmount = allocationTarget;
        long remainingHours = monthlyLimit;
        for (Candidate candidate : candidates) {
            if (remainingAmount <= 0 || remainingHours <= 0) {
                break;
            }
            long maxByShare = (long) Math.floor(monthlyLimit
                    * FATIGUE_TIME_SHARES.get(candidate.job().getBaseFatigue()));
            long requiredHours = (long) Math.ceil(remainingAmount / candidate.hourlyIncome());
            long assignedHours = Math.min(requiredHours, Math.min(maxByShare, remainingHours));
            if (assignedHours <= 0) {
                continue;
            }

            allocations.add(AllocationGoal.builder().jobId(candidate.job().getJobId())
                    .targetMonth(targetMonth).recommendHour(assignedHours).build());
            remainingHours -= assignedHours;
            remainingAmount -= Math.round(assignedHours * candidate.hourlyIncome());
        }

        // 잡 변경으로 오래된 결과가 남아 있을 수 있으므로 현재 월 사용자 결과를 교체합니다.
        allocationGoalMapper.deleteByUserIdAndTargetMonth(userId, targetMonth);
        allocations.forEach(allocationGoalMapper::insert);
        return toResponse(targetMonth, allocations, jobs);
    }

    private Candidate toCandidate(JobSummary job) {
        if (job.getBaseFatigue() == null || !FATIGUE_WEIGHTS.containsKey(job.getBaseFatigue())) {
            return null;
        }
        double hourlyIncome;
        if (HOURLY.equals(job.getSettlementType())) {
            hourlyIncome = job.getHourlyWage() == null ? 0 : job.getHourlyWage();
        } else if (PER_TASK.equals(job.getSettlementType())) {
            hourlyIncome = job.getPerTaskWage() == null || job.getTaskPerHour() == null
                    ? 0 : job.getPerTaskWage() * job.getTaskPerHour();
        } else {
            hourlyIncome = 0;
        }
        if (hourlyIncome <= 0) {
            return null;
        }
        return new Candidate(job, hourlyIncome,
                hourlyIncome * FATIGUE_WEIGHTS.get(job.getBaseFatigue()));
    }

    private boolean isRegularMonthlyJob(JobSummary job) {
        return Boolean.TRUE.equals(job.getIsRegular()) && MONTHLY.equals(job.getSettlementType())
                && job.getMonthlyWage() != null && job.getMonthlyWage() > 0;
    }

    private RecommendedWorkHoursSummary toResponse(
            String targetMonth, List<AllocationGoal> allocations, List<JobSummary> jobs) {
        Map<Long, JobSummary> byId = new HashMap<>();
        jobs.forEach(job -> byId.put(job.getJobId(), job));
        List<RecommendedJobHoursSummary> results = allocations.stream().map(allocation -> {
            JobSummary job = byId.get(allocation.getJobId());
            Candidate candidate = toCandidate(job);
            return RecommendedJobHoursSummary.builder().jobId(job.getJobId()).jobName(job.getJobName())
                    .recommendedHours(allocation.getRecommendHour())
                    .expectedIncome(Math.round(candidate.hourlyIncome() * allocation.getRecommendHour()))
                    .baseFatigue(job.getBaseFatigue()).build();
        }).toList();
        long total = results.stream().mapToLong(RecommendedJobHoursSummary::getRecommendedHours).sum();
        return RecommendedWorkHoursSummary.builder().targetMonth(targetMonth)
                .totalRecommendedHours(total).recommendedJobs(results).build();
    }

    private JobSummary toSummary(Job job) {
        return JobSummary.builder().jobId(job.getJobId()).userId(job.getUserId())
                .jobName(job.getJobName()).settlementType(job.getSettlementType().name())
                .hourlyWage(job.getHourlyWage()).monthlyWage(job.getMonthlyWage())
                .perTaskWage(job.getPerTaskWage()).taskPerHour(job.getTaskPerHour())
                .isRegular(job.getIsRegular()).baseFatigue(job.getBaseFatigue())
                .isActive(job.getIsActive()).build();
    }

    private record Candidate(JobSummary job, double hourlyIncome, double score) {}
}
