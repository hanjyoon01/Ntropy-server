package com.ntropy.defense.service;

import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import com.ntropy.defense.api.dto.command.DefenseModeReleaseCommand;
import com.ntropy.defense.api.dto.summary.FixedExpenseCheckSummary;
import com.ntropy.defense.api.dto.summary.FixedExpenseSummary;
import com.ntropy.defense.api.dto.summary.FixedExpenseMaintainStatus;
import com.ntropy.defense.api.dto.summary.ExpectedIncomeLossSummary;
import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.defense.domain.DefenseCause;
import com.ntropy.defense.domain.DefenseCalculationStatus;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.exception.DefenseErrorCode;
import com.ntropy.defense.mapper.DefenseModeMapper;
import com.ntropy.defense.port.account.FinancialCommitment;
import com.ntropy.defense.port.account.FinancialCommitmentPort;
import com.ntropy.defense.port.diagnosis.DefenseDiagnosisSnapshot;
import com.ntropy.defense.port.diagnosis.DiagnosisRecalculationPort;
import com.ntropy.defense.port.diagnosis.DiagnosisSnapshotPort;
import com.ntropy.defense.port.work.ExpectedIncomeLossPort;
import com.ntropy.defense.port.work.JobExpectedIncomeLoss;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefenseModeService {
    private static final int CRITICAL_REMAINING_DAYS = 30;
    private static final int WARNING_REMAINING_DAYS = 60;
    private static final int WARNING_REDUCTION_PERCENT = 20;

    private final DefenseModeMapper defenseModeMapper;
    private final DiagnosisSnapshotPort diagnosisSnapshotPort;
    private final DiagnosisRecalculationPort diagnosisRecalculationPort;
    private final FinancialCommitmentPort financialCommitmentPort;
    private final ExpectedIncomeLossPort expectedIncomeLossPort;
    private final Clock clock;

    @Autowired
    public DefenseModeService(
            DefenseModeMapper defenseModeMapper,
            ObjectProvider<DiagnosisSnapshotPort> diagnosisSnapshotPortProvider,
            ObjectProvider<DiagnosisRecalculationPort> diagnosisRecalculationPortProvider,
            ObjectProvider<FinancialCommitmentPort> financialCommitmentPortProvider,
            ObjectProvider<ExpectedIncomeLossPort> expectedIncomeLossPortProvider) {
        this(
                defenseModeMapper,
                diagnosisSnapshotPortProvider.getIfAvailable(() -> userId -> null),
                diagnosisRecalculationPortProvider.getObject(),
                financialCommitmentPortProvider.getIfAvailable(
                        () -> (userId, fromDate, toDate) -> Collections.emptyList()),
                expectedIncomeLossPortProvider.getIfAvailable(
                        () -> (userId, fromDate, toDate) -> Collections.emptyList()),
                Clock.system(ZoneId.of("Asia/Seoul")));
    }

    public DefenseModeService(
            DefenseModeMapper defenseModeMapper,
            DiagnosisSnapshotPort diagnosisSnapshotPort,
            FinancialCommitmentPort financialCommitmentPort,
            ExpectedIncomeLossPort expectedIncomeLossPort) {
        this(defenseModeMapper, diagnosisSnapshotPort, (userId, yearMonth) -> { }, financialCommitmentPort,
                expectedIncomeLossPort, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    public DefenseModeService(
            DefenseModeMapper defenseModeMapper,
            DiagnosisSnapshotPort diagnosisSnapshotPort,
            FinancialCommitmentPort financialCommitmentPort,
            ExpectedIncomeLossPort expectedIncomeLossPort,
            Clock clock) {
        this(defenseModeMapper, diagnosisSnapshotPort, (userId, yearMonth) -> { }, financialCommitmentPort,
                expectedIncomeLossPort, clock);
    }

    public DefenseModeService(
            DefenseModeMapper defenseModeMapper,
            DiagnosisSnapshotPort diagnosisSnapshotPort,
            DiagnosisRecalculationPort diagnosisRecalculationPort,
            FinancialCommitmentPort financialCommitmentPort,
            ExpectedIncomeLossPort expectedIncomeLossPort,
            Clock clock) {
        this.defenseModeMapper = defenseModeMapper;
        this.diagnosisSnapshotPort = diagnosisSnapshotPort;
        this.diagnosisRecalculationPort = diagnosisRecalculationPort;
        this.financialCommitmentPort = financialCommitmentPort;
        this.expectedIncomeLossPort = expectedIncomeLossPort;
        this.clock = clock;
    }

    @Transactional
    public DefenseMode enter(DefenseModeEnterCommand command) {
        validateEnterCommand(command);
        if (defenseModeMapper.findCurrentByUserId(command.getUserId()) != null) {
            throw new ServiceException(DefenseErrorCode.ALREADY_ACTIVE);
        }

        DefenseMode defenseMode = new DefenseMode();
        defenseMode.setUserId(command.getUserId());
        defenseMode.setCauseCode(parseCause(command.getCauseCode()));
        defenseMode.setUnavailableStartDate(command.getUnavailableStartDate());
        defenseMode.setExpectedReturnDate(command.getExpectedReturnDate());
        LocalDate today = LocalDate.now(clock);
        if (command.getUnavailableStartDate().isAfter(today)) {
            defenseMode.setStatus(DefenseModeStatus.SCHEDULED);
        } else {
            recalculateAndApplyDiagnosisSnapshot(defenseMode, today);
            defenseMode.setStatus(DefenseModeStatus.ACTIVE);
        }
        defenseModeMapper.insert(defenseMode);
        return defenseModeMapper.findById(defenseMode.getDefenseId());
    }

    public DefenseMode getCurrent(Long userId) {
        if (userId == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }
        DefenseMode defenseMode = defenseModeMapper.findCurrentByUserId(userId);
        if (defenseMode == null) {
            throw new ServiceException(DefenseErrorCode.NOT_FOUND);
        }
        return defenseMode;
    }

    @Transactional
    public int activateScheduledModes() {
        LocalDate today = LocalDate.now(clock);
        int activatedCount = 0;
        for (DefenseMode defenseMode : defenseModeMapper.findScheduledToActivate(today)) {
            recalculateAndApplyDiagnosisSnapshot(defenseMode, today);
            defenseMode.setStatus(DefenseModeStatus.ACTIVE);
            activatedCount += defenseModeMapper.activate(defenseMode);
        }
        return activatedCount;
    }

    public Integer getCurrentDDay(DefenseMode defenseMode) {
        return currentDefenseState(defenseMode).dDay;
    }

    public List<DefenseMode> getCalendarPeriods(Long userId, LocalDate from, LocalDate to) {
        if (userId == null || from == null || to == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }
        if (to.isBefore(from)) {
            throw new ServiceException(DefenseErrorCode.INVALID_PERIOD);
        }
        return defenseModeMapper.findCalendarPeriods(userId, from, to);
    }

    public FixedExpenseCheckSummary getFixedExpenseCheck(DefenseMode defenseMode) {
        List<FinancialCommitment> commitments = financialCommitmentPort.findFinancialCommitments(
                defenseMode.getUserId(),
                defenseMode.getUnavailableStartDate(),
                defenseMode.getExpectedReturnDate());
        if (commitments == null) {
            commitments = Collections.emptyList();
        }

        List<FixedExpenseSummary> expenses = commitments.stream()
                .map(commitment -> toFixedExpense(defenseMode, commitment))
                .collect(Collectors.toList());
        long totalExpectedAmount = commitments.stream()
                .map(FinancialCommitment::expectedAmount)
                .filter(amount -> amount != null && amount > 0)
                .mapToLong(Long::longValue)
                .sum();
        return new FixedExpenseCheckSummary(totalExpectedAmount, expenses);
    }

    public ExpectedIncomeLossSummary getExpectedIncomeLoss(DefenseMode defenseMode) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate periodStartDate = laterOf(defenseMode.getUnavailableStartDate(), currentMonth.atDay(1));
        LocalDate periodEndDate = earlierOf(defenseMode.getExpectedReturnDate(), currentMonth.atEndOfMonth());
        if (periodStartDate.isAfter(periodEndDate)) {
            return new ExpectedIncomeLossSummary(0L, null, null, "NO_SCHEDULE", Collections.emptyList());
        }

        List<JobExpectedIncomeLoss> jobs = expectedIncomeLossPort.findExpectedIncomeLossByJob(
                defenseMode.getUserId(), periodStartDate, periodEndDate);
        if (jobs == null || jobs.isEmpty()) {
            return new ExpectedIncomeLossSummary(
                    0L, periodStartDate, periodEndDate, "NO_SCHEDULE", Collections.emptyList());
        }

        long totalAmount = jobs.stream()
                .map(JobExpectedIncomeLoss::expectedIncomeLoss)
                .filter(income -> income != null && income > 0)
                .mapToLong(Long::longValue)
                .sum();
        long calculatedJobCount = jobs.stream()
                .filter(job -> job.expectedIncomeLoss() != null)
                .count();
        String calculationStatus;
        if (calculatedJobCount == 0) {
            calculationStatus = "INSUFFICIENT";
        } else if (calculatedJobCount < jobs.size()) {
            calculationStatus = "PARTIALLY_CALCULATED";
        } else {
            calculationStatus = "CALCULATED";
        }
        // ExpectedIncomeLossSummary는 defense의 외부 발행 계약이라 work의 JobExpectedIncomeLossSummary를
        // 그대로 담는다 - 여기서만 defense 포트 타입을 그 모양으로 되돌려 번역한다.
        List<JobExpectedIncomeLossSummary> jobSummaries = jobs.stream()
                .map(job -> new JobExpectedIncomeLossSummary(job.jobId(), job.jobName(), job.expectedIncomeLoss()))
                .toList();
        return new ExpectedIncomeLossSummary(
                totalAmount, periodStartDate, periodEndDate, calculationStatus, jobSummaries);
    }

    @Transactional
    public DefenseMode release(Long defenseId, DefenseModeReleaseCommand command) {
        if (defenseId == null || command == null || command.getUserId() == null || command.getReturnDate() == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }

        DefenseMode defenseMode = defenseModeMapper.findById(defenseId);
        if (defenseMode == null) {
            throw new ServiceException(DefenseErrorCode.NOT_FOUND);
        }
        if (!command.getUserId().equals(defenseMode.getUserId())) {
            throw new ServiceException(DefenseErrorCode.ACCESS_DENIED);
        }
        if (defenseMode.getStatus() != DefenseModeStatus.ACTIVE) {
            throw new ServiceException(DefenseErrorCode.NOT_ACTIVE);
        }
        if (command.getReturnDate().isBefore(defenseMode.getUnavailableStartDate())) {
            throw new ServiceException(DefenseErrorCode.INVALID_PERIOD);
        }

        defenseMode.setReturnDate(command.getReturnDate());
        defenseMode.setStatus(DefenseModeStatus.RELEASED);
        defenseModeMapper.release(defenseMode);
        return defenseModeMapper.findById(defenseId);
    }

    private void validateEnterCommand(DefenseModeEnterCommand command) {
        if (command == null || command.getUserId() == null || command.getCauseCode() == null
                || command.getUnavailableStartDate() == null || command.getExpectedReturnDate() == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }
        if (command.getExpectedReturnDate().isBefore(command.getUnavailableStartDate())) {
            throw new ServiceException(DefenseErrorCode.INVALID_PERIOD);
        }
    }

    private DefenseCause parseCause(String causeCode) {
        try {
            DefenseCause cause = DefenseCause.valueOf(causeCode);
            if (!cause.isSelectable()) {
                throw new ServiceException(DefenseErrorCode.INVALID_CAUSE);
            }
            return cause;
        } catch (IllegalArgumentException e) {
            throw new ServiceException(DefenseErrorCode.INVALID_CAUSE);
        }
    }

    private void applyDiagnosisSnapshot(DefenseMode defenseMode, DefenseDiagnosisSnapshot snapshot) {
        Long reserveAmount = snapshot == null ? null : snapshot.reserveAmount();
        Long safeAssetAmount = snapshot == null ? null : snapshot.safeAssetAmount();
        Long availableAssets = sumNullable(reserveAmount, safeAssetAmount);
        Long averageMonthlyExpense = snapshot == null ? null : snapshot.averageMonthlyExpense();

        defenseMode.setReserveAmountSnapshot(reserveAmount);
        defenseMode.setSafeAssetAmountSnapshot(safeAssetAmount);
        defenseMode.setAvailableAssetsSnapshot(availableAssets);
        defenseMode.setAverageMonthlyExpense(averageMonthlyExpense);

        if (availableAssets == null) {
            defenseMode.setCalculationStatus(DefenseCalculationStatus.DIAGNOSIS_REQUIRED);
            return;
        }
        if (averageMonthlyExpense == null || averageMonthlyExpense <= 0) {
            defenseMode.setCalculationStatus(DefenseCalculationStatus.EXPENSE_DATA_REQUIRED);
            return;
        }

        long dailyExpense = (long) Math.ceil(averageMonthlyExpense / 30.0);
        long calculatedDays = availableAssets / dailyExpense;
        defenseMode.setDailyExpense(dailyExpense);
        defenseMode.setDDay(calculatedDays > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) calculatedDays);
        defenseMode.setCalculationStatus(DefenseCalculationStatus.CALCULATED);
    }

    private void recalculateAndApplyDiagnosisSnapshot(DefenseMode defenseMode, LocalDate activationDate) {
        diagnosisRecalculationPort.recalculate(defenseMode.getUserId(), YearMonth.from(activationDate));
        applyDiagnosisSnapshot(defenseMode, diagnosisSnapshotPort.getDefenseSnapshot(defenseMode.getUserId()));
    }

    private Long sumNullable(Long first, Long second) {
        if (first == null && second == null) {
            return null;
        }
        return (first == null ? 0L : first) + (second == null ? 0L : second);
    }

    private FixedExpenseSummary toFixedExpense(
            DefenseMode defenseMode,
            FinancialCommitment commitment) {
        CurrentDefenseState currentState = currentDefenseState(defenseMode);
        Integer dDayAfter = null;
        Integer dDayReduction = null;
        Long expectedAmount = commitment.expectedAmount();
        if (currentState.dDay != null
                && currentState.availableAssets != null
                && defenseMode.getDailyExpense() != null
                && defenseMode.getDailyExpense() > 0
                && expectedAmount != null
                && expectedAmount >= 0
                && !"INSUFFICIENT".equals(commitment.amountStatus())) {
            long assetsAfterPayment = Math.max(currentState.availableAssets - expectedAmount, 0L);
            long calculatedDays = assetsAfterPayment / defenseMode.getDailyExpense();
            dDayAfter = calculatedDays > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) calculatedDays;
            dDayReduction = Math.max(currentState.dDay - dDayAfter, 0);
        }

        FixedExpenseMaintainStatus maintainStatus = maintainStatus(
                currentState.availableAssets,
                currentState.dDay,
                expectedAmount,
                dDayAfter,
                dDayReduction,
                commitment.amountStatus());
        return new FixedExpenseSummary(
                commitment.commitmentId(),
                commitment.accountId(),
                commitment.expenseType(),
                expenseName(commitment.expenseType()),
                commitment.productName(),
                commitment.outstandingBalance(),
                expectedAmount,
                commitment.nextPaymentDate(),
                commitment.amountStatus(),
                commitment.dateStatus(),
                currentState.dDay,
                dDayAfter,
                dDayReduction,
                maintainStatus);
    }

    private CurrentDefenseState currentDefenseState(DefenseMode defenseMode) {
        if (defenseMode == null
                || defenseMode.getAvailableAssetsSnapshot() == null
                || defenseMode.getDailyExpense() == null
                || defenseMode.getDailyExpense() <= 0
                || defenseMode.getDDay() == null) {
            return new CurrentDefenseState(null, null);
        }

        long elapsedDays = Math.max(
                ChronoUnit.DAYS.between(defenseMode.getUnavailableStartDate(), LocalDate.now(clock)),
                0L);
        long availableAssets = defenseMode.getAvailableAssetsSnapshot();
        long dailyExpense = defenseMode.getDailyExpense();
        long remainingAssets;
        if (elapsedDays > availableAssets / dailyExpense) {
            remainingAssets = 0L;
        } else {
            remainingAssets = availableAssets - dailyExpense * elapsedDays;
        }

        long calculatedDays = remainingAssets / dailyExpense;
        int remainingDDay = calculatedDays > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) calculatedDays;
        return new CurrentDefenseState(remainingAssets, remainingDDay);
    }

    private static class CurrentDefenseState {
        private final Long availableAssets;
        private final Integer dDay;

        private CurrentDefenseState(Long availableAssets, Integer dDay) {
            this.availableAssets = availableAssets;
            this.dDay = dDay;
        }
    }

    private FixedExpenseMaintainStatus maintainStatus(
            Long availableAssets,
            Integer dDayBefore,
            Long expectedAmount,
            Integer dDayAfter,
            Integer dDayReduction,
            String amountStatus) {
        if (availableAssets == null
                || dDayBefore == null
                || expectedAmount == null
                || expectedAmount < 0
                || dDayAfter == null
                || dDayReduction == null
                || "INSUFFICIENT".equals(amountStatus)) {
            return FixedExpenseMaintainStatus.UNDETERMINED;
        }
        if (expectedAmount > availableAssets || dDayAfter < CRITICAL_REMAINING_DAYS) {
            return FixedExpenseMaintainStatus.REVIEW_SUSPENSION;
        }
        if (dDayAfter < WARNING_REMAINING_DAYS
                || reductionPercentAtLeast(dDayBefore, dDayReduction, WARNING_REDUCTION_PERCENT)) {
            return FixedExpenseMaintainStatus.DIFFICULT;
        }
        return FixedExpenseMaintainStatus.NORMAL;
    }

    private boolean reductionPercentAtLeast(Integer dDayBefore, Integer dDayReduction, int thresholdPercent) {
        if (dDayBefore <= 0) {
            return dDayReduction > 0;
        }
        return (long) dDayReduction * 100 >= (long) dDayBefore * thresholdPercent;
    }

    private String expenseName(String expenseType) {
        if ("SAVING_PAYMENT".equals(expenseType)) {
            return "적금납입";
        }
        if ("LOAN_REPAYMENT".equals(expenseType)) {
            return "대출상환";
        }
        if ("INSURANCE_PREMIUM".equals(expenseType)) {
            return "보험료";
        }
        return "금융지출";
    }

    private LocalDate laterOf(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate earlierOf(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
