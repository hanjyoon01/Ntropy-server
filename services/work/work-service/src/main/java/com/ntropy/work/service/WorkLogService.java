package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.WorkLogSettlementStatusCalculator;
import com.ntropy.work.domain.WorkLogStatus;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobPlatformMapping;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.exception.WorkErrorCode;
import com.ntropy.work.mapper.JobPlatformMappingMapper;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkLogService {

    private static final String STATUS_PLANNED = WorkLogStatus.PLANNED;
    private static final String STATUS_CONFIRMED = WorkLogStatus.CONFIRMED;
    private static final String TRIGGER_ON_DEMAND = "ON_DEMAND";

    private final WorkLogMapper workLogMapper;
    private final JobService jobService;
    private final JobPlatformMappingMapper jobPlatformMappingMapper;
    private final PlatformMapper platformMapper;
    private final WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;

    /**
     * 근무 계획 등록. fatigue 미입력 시 job.baseFatigue를 기본값으로 채운다.
     * estimated_income은 taskPerHour 기반 예측값이다(taskCount는 아직 없음).
     */
    @Transactional
    public WorkLog registerPlan(WorkLog workLog) {
        validatePlan(workLog);
        validateNoOverlap(workLog.getUserId(), workLog.getWorkDate(), workLog.getStartTime(), workLog.getEndTime(), null);
        Job job = jobService.findById(workLog.getJobId());

        if (!isFatigueProvided(workLog.getFatigue())) {
            workLog.setFatigue(job.getBaseFatigue().longValue());
        }
        workLog.setTaskCount(null);
        workLog.setEstimatedIncome(
                calculateEstimatedIncome(job, workLog.getStartTime(), workLog.getEndTime(), null));
        workLog.setStatus(STATUS_PLANNED);
        workLog.setSettlementStatus(SettlementStatus.NONE);

        workLogMapper.insert(workLog);
        return workLog;
    }

    /**
     * 계획 외 근무일지 등록. 실제 데이터를 받아 즉시 CONFIRMED로 생성한다.
     * 잡에 매핑된 플랫폼이 여러 개면 소득을 균등 분배한다(예: 2개면 반반) - 프론트에서
     * 따로 입력받지 않는다.
     */
    @Transactional
    public WorkLog registerActual(WorkLog workLog) {
        validateActual(workLog);
        validateNoOverlap(workLog.getUserId(), workLog.getWorkDate(), workLog.getStartTime(), workLog.getEndTime(), null);
        Job job = jobService.findById(workLog.getJobId());
        validateTaskCountIfPerTask(job, workLog.getTaskCount());

        workLog.setEstimatedIncome(
                calculateEstimatedIncome(job, workLog.getStartTime(), workLog.getEndTime(), workLog.getTaskCount()));
        workLog.setStatus(STATUS_CONFIRMED);

        List<WorkLogPlatformIncome> incomes = resolvePlatformIncomes(job.getJobId(), workLog.getEstimatedIncome());
        workLog.setSettlementStatus(WorkLogSettlementStatusCalculator.calculate(incomes));

        workLogMapper.insert(workLog);
        saveIncomes(workLog.getLogId(), incomes);
        return workLog;
    }

    /**
     * 근무일지 수정. PLANNED 상태는 항상 가능하다. CONFIRMED 상태는 아직 어느 플랫폼도
     * 정산되지 않은 경우(settlementStatus == PENDING)에 한해 허용한다 - 하나라도
     * 실제 입금과 매칭(PARTIAL/COMPLETED)된 이후에는 그 기록을 보존해야 하므로 여전히
     * 수정할 수 없고 삭제만 가능하다. CONFIRMED 상태를 수정하면 소득이 바뀔 수 있어
     * WORK_LOG_PLATFORM_INCOME을 지우고 다시 배분한다.
     * 넘어온 필드만 덮어쓰고 나머지는 기존 값을 유지한다.
     *
     * @param requesterUserId 요청자 userId. 근무일지 소유자와 다르면 예외
     */
    @Transactional
    public WorkLog editWorkLog(Long requesterUserId, Long logId, WorkLog patch) {
        WorkLog existing = findById(logId);
        verifyOwnership(requesterUserId, existing.getUserId(), logId);
        boolean wasConfirmed = STATUS_CONFIRMED.equals(existing.getStatus());
        if (wasConfirmed && existing.getSettlementStatus() != SettlementStatus.PENDING) {
            throw new ServiceException(WorkErrorCode.WORK_LOG_SETTLEMENT_IN_PROGRESS, "logId=" + logId);
        }
        applyPatch(existing, patch);
        validateNoOverlap(existing.getUserId(), existing.getWorkDate(), existing.getStartTime(), existing.getEndTime(),
                existing.getLogId());

        Job job = jobService.findById(existing.getJobId());
        if (wasConfirmed) {
            validateTaskCountIfPerTask(job, existing.getTaskCount());
        }
        existing.setEstimatedIncome(
                calculateEstimatedIncome(job, existing.getStartTime(), existing.getEndTime(), existing.getTaskCount()));

        if (wasConfirmed) {
            workLogPlatformIncomeMapper.deleteByLogId(existing.getLogId());
            List<WorkLogPlatformIncome> incomes = resolvePlatformIncomes(job.getJobId(), existing.getEstimatedIncome());
            existing.setSettlementStatus(WorkLogSettlementStatusCalculator.calculate(incomes));
            workLogMapper.update(existing);
            saveIncomes(existing.getLogId(), incomes);
        } else {
            workLogMapper.update(existing);
        }
        return existing;
    }

    /**
     * 근무일지 확정. PLANNED 상태에서만 가능하다. jobId/시간/건수/피로도를 전부
     * 받아 덮어쓸 수 있고(사진 화면 기준), 최종적으로 CONFIRMED로 전환한다.
     * 잡에 매핑된 플랫폼이 여러 개면 소득을 균등 분배한다(예: 2개면 반반) - 프론트에서
     * 따로 입력받지 않는다.
     *
     * @param requesterUserId 요청자 userId. 근무일지 소유자와 다르면 예외
     */
    @Transactional
    public WorkLog confirmWorkLog(Long requesterUserId, Long logId, WorkLog patch) {
        WorkLog existing = findById(logId);
        verifyOwnership(requesterUserId, existing.getUserId(), logId);
        if (STATUS_CONFIRMED.equals(existing.getStatus())) {
            throw new ServiceException(WorkErrorCode.WORK_LOG_ALREADY_CONFIRMED, "logId=" + logId);
        }
        applyPatch(existing, patch);
        validateNoOverlap(existing.getUserId(), existing.getWorkDate(), existing.getStartTime(), existing.getEndTime(),
                existing.getLogId());

        Job job = jobService.findById(existing.getJobId());
        validateTaskCountIfPerTask(job, existing.getTaskCount());

        existing.setEstimatedIncome(
                calculateEstimatedIncome(job, existing.getStartTime(), existing.getEndTime(), existing.getTaskCount()));
        existing.setStatus(STATUS_CONFIRMED);

        List<WorkLogPlatformIncome> incomes = resolvePlatformIncomes(job.getJobId(), existing.getEstimatedIncome());
        existing.setSettlementStatus(WorkLogSettlementStatusCalculator.calculate(incomes));

        workLogMapper.update(existing);
        saveIncomes(existing.getLogId(), incomes);
        return existing;
    }

    @Transactional
    public void deleteWorkLog(Long requesterUserId, Long logId) {
        WorkLog existing = findById(logId);
        verifyOwnership(requesterUserId, existing.getUserId(), logId);
        workLogPlatformIncomeMapper.deleteByLogId(logId);
        workLogMapper.deleteById(logId);
    }

    public WorkLog findById(Long logId) {
        WorkLog workLog = workLogMapper.findById(logId);
        if (workLog == null) {
            throw new ServiceException(WorkErrorCode.WORK_LOG_NOT_FOUND, "logId=" + logId);
        }
        return workLog;
    }

    /** 요청자가 이 근무일지의 소유자가 아니면 예외를 던진다. */
    private void verifyOwnership(Long requesterUserId, Long ownerUserId, Long logId) {
        if (!ownerUserId.equals(requesterUserId)) {
            throw new ServiceException(WorkErrorCode.WORK_LOG_ACCESS_DENIED, "logId=" + logId);
        }
    }

    /**
     * 잡에 매핑된 플랫폼 기준으로 소득을 균등 분배한다. 매핑된 플랫폼이 없으면 추적 불가라
     * 빈 리스트, 1개면 전액 그대로, 2개 이상이면 나눠서(나머지는 앞쪽 플랫폼부터 1원씩)
     * 배정한다.
     */
    private List<WorkLogPlatformIncome> resolvePlatformIncomes(Long jobId, Long estimatedIncome) {
        long totalIncome = estimatedIncome == null ? 0L : estimatedIncome;
        List<Long> mappedPlatformIds = jobPlatformMappingMapper.findByJobId(jobId).stream()
                .map(JobPlatformMapping::getPlatformId)
                .toList();

        if (mappedPlatformIds.isEmpty()) {
            return List.of();
        }

        int count = mappedPlatformIds.size();
        long base = totalIncome / count;
        long remainder = totalIncome % count;

        List<WorkLogPlatformIncome> incomes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long amount = base + (i < remainder ? 1 : 0);
            incomes.add(toIncome(mappedPlatformIds.get(i), amount));
        }
        return incomes;
    }

    /** ON_DEMAND 플랫폼이면 이 근무일지가 확정되는 시점에 그 행만 즉시 완료로 만든다. */
    private WorkLogPlatformIncome toIncome(Long platformId, long amount) {
        Platform platform = platformMapper.findById(platformId);
        SettlementStatus status = TRIGGER_ON_DEMAND.equals(platform.getSettlementTriggerType())
                ? SettlementStatus.COMPLETED
                : SettlementStatus.PENDING;
        return WorkLogPlatformIncome.builder()
                .platformId(platformId)
                .expectedAmount(amount)
                .settlementStatus(status)
                .build();
    }

    private void saveIncomes(Long logId, List<WorkLogPlatformIncome> incomes) {
        for (WorkLogPlatformIncome income : incomes) {
            income.setLogId(logId);
            workLogPlatformIncomeMapper.insert(income);
        }
    }

    private void applyPatch(WorkLog existing, WorkLog patch) {
        if (patch.getJobId() != null) {
            existing.setJobId(patch.getJobId());
        }
        if (patch.getStartTime() != null) {
            existing.setStartTime(patch.getStartTime());
        }
        if (patch.getEndTime() != null) {
            existing.setEndTime(patch.getEndTime());
        }
        if (patch.getTaskCount() != null) {
            existing.setTaskCount(patch.getTaskCount());
        }
        if (isFatigueProvided(patch.getFatigue())) {
            existing.setFatigue(patch.getFatigue());
        }
    }

    /**
     * 자정을 넘기는 근무(예: 23:30~02:00)는 24시간을 더해 정상 계산한다.
     */
    private Long calculateEstimatedIncome(Job job, LocalTime startTime, LocalTime endTime, Long taskCount) {
        if (startTime == null || endTime == null) {
            return null;
        }
        if (startTime.equals(endTime)) {
            throw new ServiceException(WorkErrorCode.INVALID_WORK_TIME_RANGE);
        }
        double hours = WorkTimeUtils.durationMinutes(startTime, endTime) / 60.0;

        switch (job.getSettlementType()) {
            case HOURLY:
                return job.getHourlyWage() == null ? null : Math.round(job.getHourlyWage() * hours);
            case PER_TASK:
                if (taskCount != null) {
                    return job.getPerTaskWage() == null ? null : job.getPerTaskWage() * taskCount;
                }
                if (job.getPerTaskWage() == null || job.getTaskPerHour() == null) {
                    return null;
                }
                return Math.round(job.getPerTaskWage() * job.getTaskPerHour() * hours);
            case MONTHLY:
                return null;
            default:
                throw new IllegalStateException("알 수 없는 정산 방식입니다: " + job.getSettlementType());
        }
    }

    /**
     * 같은 userId + workDate 내에서 시간대가 겹치는 다른 근무일지가 있는지 검사한다(잡 무관).
     * excludeLogId는 수정/확정 시 자기 자신의 기존 레코드를 비교 대상에서 제외하기 위함이다.
     */
    private void validateNoOverlap(Long userId, LocalDate workDate, LocalTime startTime, LocalTime endTime,
                                    Long excludeLogId) {
        for (WorkLog other : workLogMapper.findByUserIdAndWorkDate(userId, workDate)) {
            if (excludeLogId != null && excludeLogId.equals(other.getLogId())) {
                continue;
            }
            if (WorkTimeUtils.isOverlapping(startTime, endTime, other.getStartTime(), other.getEndTime())) {
                throw new ServiceException(WorkErrorCode.WORK_LOG_TIME_OVERLAP, "workDate=" + workDate);
            }
        }
    }

    private void validateTaskCountIfPerTask(Job job, Long taskCount) {
        if (SettlementType.PER_TASK.equals(job.getSettlementType()) && taskCount == null) {
            throw new ServiceException(WorkErrorCode.TASK_COUNT_REQUIRED);
        }
    }

    private void validatePlan(WorkLog workLog) {
        if (workLog.getUserId() == null) {
            throw new ServiceException(WorkErrorCode.WORK_LOG_USER_ID_REQUIRED);
        }
        if (workLog.getJobId() == null) {
            throw new ServiceException(WorkErrorCode.WORK_LOG_JOB_ID_REQUIRED);
        }
        if (workLog.getWorkDate() == null) {
            throw new ServiceException(WorkErrorCode.WORK_DATE_REQUIRED);
        }
        if (workLog.getStartTime() == null || workLog.getEndTime() == null) {
            throw new ServiceException(WorkErrorCode.WORK_TIME_REQUIRED);
        }
    }

    private void validateActual(WorkLog workLog) {
        validatePlan(workLog);
        if (!isFatigueProvided(workLog.getFatigue())) {
            throw new ServiceException(WorkErrorCode.FATIGUE_REQUIRED_FOR_ACTUAL);
        }
    }

    /** fatigue는 1~5 척도이므로 null과 0은 둘 다 "미입력"으로 취급한다. */
    private boolean isFatigueProvided(Long fatigue) {
        return fatigue != null && fatigue != 0;
    }
}
