package com.ntropy.work.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobSchedule;
import com.ntropy.work.exception.WorkErrorCode;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.JobScheduleMapper;
import com.ntropy.work.mapper.AllocationGoalMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobMapper jobMapper;
    private final JobScheduleMapper jobScheduleMapper;
    private final CategoryService categoryService;
    private final AllocationGoalMapper allocationGoalMapper;
    private final RecommendedWorkHoursService recommendedWorkHoursService;

    /**
     * 잡 등록. 정기근무 스케줄이 있으면 같이 등록한다(둘 다 성공하거나 둘 다 롤백).
     *
     * @param job       jobId/createdAt/updatedAt/isActive는 이 메서드가 채우므로 호출부에서 안 넣어도 됨
     * @param schedules 정기근무 아니면 null 또는 빈 리스트
     */
    @Transactional
    public Job registerJob(Job job, List<JobSchedule> schedules) {
        validate(job);
        validateScheduleConsistency(job, schedules);
        validateScheduleOverlap(job.getUserId(), schedules, null);
        categoryService.findById(job.getCategoryId());

        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        if (job.getIsActive() == null) {
            job.setIsActive(true);
        }
        job.setMonthlyExpectedIncome(calculateMonthlyExpectedIncome(job, safe(schedules)));

        jobMapper.insert(job);

        for (JobSchedule schedule : safe(schedules)) {
            schedule.setJobId(job.getJobId());
            jobScheduleMapper.insert(schedule);
        }

        // 잡이 추가되면 기존 이번 달 추천 결과는 더 이상 최신이 아니므로 무효화하고 즉시 재계산합니다.
        allocationGoalMapper.deleteByUserIdAndTargetMonth(job.getUserId(), currentMonth());
        recommendedWorkHoursService.getCurrentMonthRecommendedWorkHours(job.getUserId());

        return job;
    }

    public Job findById(Long jobId) {
        Job job = jobMapper.findById(jobId);
        if (job == null) {
            throw new ServiceException(WorkErrorCode.JOB_NOT_FOUND, "jobId=" + jobId);
        }
        return job;
    }

    public List<Job> findByUserId(Long userId) {
        return jobMapper.findByUserId(userId);
    }

    public List<JobSchedule> findSchedulesByJobId(Long jobId) {
        return jobScheduleMapper.findByJobId(jobId);
    }

    /**
     * 잡 수정. 스케줄은 부분 수정이 아니라 전체 교체다 — 넘어온 리스트로
     * 기존 스케줄을 다 지우고 새로 넣는다(PUT이 나머지 필드를 통째로 덮어쓰는 것과 동일한 규칙).
     *
     * @param requesterUserId 요청자 userId. 잡 소유자와 다르면 예외
     * @param schedules       정기잡 아니면 null 또는 빈 리스트
     */
    @Transactional
    public Job updateJob(Long requesterUserId, Job job, List<JobSchedule> schedules) {
        Job existing = findById(job.getJobId());
        verifyOwnership(requesterUserId, existing.getUserId(), job.getJobId());
        job.setUserId(existing.getUserId());

        validate(job);
        validateScheduleConsistency(job, schedules);
        validateScheduleOverlap(job.getUserId(), schedules, job.getJobId());
        categoryService.findById(job.getCategoryId());

        job.setIsActive(existing.getIsActive());
        job.setCreatedAt(existing.getCreatedAt());
        job.setUpdatedAt(LocalDateTime.now());
        job.setMonthlyExpectedIncome(calculateMonthlyExpectedIncome(job, safe(schedules)));
        jobMapper.update(job);

        jobScheduleMapper.deleteByJobId(job.getJobId());
        for (JobSchedule schedule : safe(schedules)) {
            schedule.setJobId(job.getJobId());
            jobScheduleMapper.insert(schedule);
        }

        // 시급·정산 방식·피로도·활성 상태 등이 변경될 수 있으므로 즉시 재계산합니다.
        allocationGoalMapper.deleteByUserIdAndTargetMonth(existing.getUserId(), currentMonth());
        recommendedWorkHoursService.getCurrentMonthRecommendedWorkHours(existing.getUserId());

        return job;
    }

    @Transactional
    public void deactivateJob(Long requesterUserId, Long jobId) {
        Job job = findById(jobId);
        verifyOwnership(requesterUserId, job.getUserId(), jobId);
        job.setIsActive(false);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        allocationGoalMapper.deleteByUserIdAndTargetMonth(job.getUserId(), currentMonth());
        recommendedWorkHoursService.getCurrentMonthRecommendedWorkHours(job.getUserId());
    }

    /** 요청자가 이 잡의 소유자가 아니면 예외를 던진다. */
    private void verifyOwnership(Long requesterUserId, Long ownerUserId, Long jobId) {
        if (!ownerUserId.equals(requesterUserId)) {
            throw new ServiceException(WorkErrorCode.JOB_ACCESS_DENIED, "jobId=" + jobId);
        }
    }

    private void validate(Job job) {
        if (!StringUtils.hasText(job.getJobName())) {
            throw new ServiceException(WorkErrorCode.JOB_NAME_REQUIRED);
        }
        if (job.getCategoryId() == null) {
            throw new ServiceException(WorkErrorCode.CATEGORY_ID_REQUIRED);
        }
        if (job.getSettlementType() == null) {
            throw new ServiceException(WorkErrorCode.SETTLEMENT_TYPE_REQUIRED);
        }
        validateSettlementFields(job);
        if (job.getIsRegular() == null) {
            throw new ServiceException(WorkErrorCode.IS_REGULAR_REQUIRED);
        }
        if (job.getBaseFatigue() == null) {
            throw new ServiceException(WorkErrorCode.BASE_FATIGUE_REQUIRED);
        }
    }

    /**
     * settlement_type에 맞는 임금 필드가 채워졌는지 검사한다.
     */
    private void validateSettlementFields(Job job) {
        switch (job.getSettlementType()) {
            case HOURLY:
                if (job.getHourlyWage() == null) {
                    throw new ServiceException(WorkErrorCode.HOURLY_WAGE_REQUIRED);
                }
                break;
            case PER_TASK:
                if (job.getPerTaskWage() == null || job.getTaskPerHour() == null) {
                    throw new ServiceException(WorkErrorCode.PER_TASK_FIELDS_REQUIRED);
                }
                break;
            case MONTHLY:
                if (job.getMonthlyWage() == null) {
                    throw new ServiceException(WorkErrorCode.MONTHLY_WAGE_REQUIRED);
                }
                break;
            default:
                throw new IllegalStateException("알 수 없는 정산 방식입니다: " + job.getSettlementType());
        }
    }

    private List<JobSchedule> safe(List<JobSchedule> schedules) {
        return schedules == null ? Collections.emptyList() : schedules;
    }

    private String currentMonth() {
        return YearMonth.now().toString();
    }

    /**
     * 방어모드 예상 손실소득 계산에 쓰이는 월 환산 예상 소득을 정산 방식별로 계산한다.
     *
     * <p>MONTHLY는 월급을 그대로 쓰고, HOURLY는 정기근무 스케줄의 주간 근무시간을
     * 월(30일)로 환산해 시급을 곱한다. PER_TASK는 계산 방식이 정해지지 않아 null을 반환하며,
     * 방어모드는 이를 "계산 불가"로 처리한다.</p>
     */
    private Long calculateMonthlyExpectedIncome(Job job, List<JobSchedule> schedules) {
        switch (job.getSettlementType()) {
            case MONTHLY:
                return job.getMonthlyWage().longValue();
            case HOURLY:
                long weeklyMinutes = schedules.stream()
                        .mapToLong(s -> WorkTimeUtils.durationMinutes(s.getStartTime(), s.getEndTime()))
                        .sum();
                if (weeklyMinutes == 0) {
                    return null;
                }
                double monthlyHours = weeklyMinutes / 60.0 * (30.0 / 7.0);
                return Math.round(monthlyHours * job.getHourlyWage());
            default:
                return null;
        }
    }

    private void validateScheduleConsistency(Job job, List<JobSchedule> schedules) {
        boolean hasSchedules = !safe(schedules).isEmpty();
        if (Boolean.TRUE.equals(job.getIsRegular()) && !hasSchedules) {
            throw new ServiceException(WorkErrorCode.REGULAR_JOB_SCHEDULE_REQUIRED);
        }
        if (Boolean.FALSE.equals(job.getIsRegular()) && hasSchedules) {
            throw new ServiceException(WorkErrorCode.NON_REGULAR_JOB_SCHEDULE_NOT_ALLOWED);
        }
    }

    /**
     * 신규 스케줄끼리, 그리고 같은 유저의 다른 잡에 이미 등록된 스케줄과 같은 요일에
     * 시간대가 겹치지 않는지 검사한다. 한 사람이 동시에 두 근무를 뛸 수 없기 때문에
     * 잡 단위가 아니라 유저 단위로 검사한다.
     *
     * @param excludeJobId 수정 시 자기 자신의 기존 스케줄을 비교 대상에서 빼기 위함(신규 등록이면 null)
     */
    private void validateScheduleOverlap(Long userId, List<JobSchedule> schedules, Long excludeJobId) {
        List<JobSchedule> newSchedules = safe(schedules);
        if (newSchedules.isEmpty()) {
            return;
        }

        List<JobSchedule> allSchedules = new ArrayList<>(newSchedules);
        for (Job existingJob : jobMapper.findByUserId(userId)) {
            if (existingJob.getJobId().equals(excludeJobId)) {
                continue;
            }
            allSchedules.addAll(jobScheduleMapper.findByJobId(existingJob.getJobId()));
        }

        for (JobSchedule newSchedule : newSchedules) {
            for (JobSchedule other : allSchedules) {
                if (other == newSchedule || !other.getDayOfWeek().equals(newSchedule.getDayOfWeek())) {
                    continue;
                }
                if (WorkTimeUtils.isOverlapping(newSchedule.getStartTime(), newSchedule.getEndTime(),
                        other.getStartTime(), other.getEndTime())) {
                    throw new ServiceException(WorkErrorCode.SCHEDULE_OVERLAP, "dayOfWeek=" + newSchedule.getDayOfWeek());
                }
            }
        }
    }
}
