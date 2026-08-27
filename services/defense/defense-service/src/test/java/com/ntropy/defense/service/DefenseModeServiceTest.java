package com.ntropy.defense.service;

import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import com.ntropy.defense.api.dto.command.DefenseModeReleaseCommand;
import com.ntropy.defense.api.dto.summary.FixedExpenseCheckSummary;
import com.ntropy.defense.api.dto.summary.FixedExpenseMaintainStatus;
import com.ntropy.defense.api.dto.summary.ExpectedIncomeLossSummary;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseCalculationStatus;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.mapper.DefenseModeMapper;
import com.ntropy.defense.port.account.FinancialCommitment;
import com.ntropy.defense.port.diagnosis.DefenseDiagnosisSnapshot;
import com.ntropy.defense.port.work.JobExpectedIncomeLoss;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefenseModeServiceTest {
    private final MemoryMapper mapper = new MemoryMapper();
    private final DefenseModeService service = new DefenseModeService(
            mapper,
            userId -> new DefenseDiagnosisSnapshot(1_280_000L, 3_400_000L, 3_300_000L),
            (userId, fromDate, toDate) -> Collections.emptyList(),
            (userId, fromDate, toDate) -> Collections.emptyList());

    @Test
    void entersAndReleasesDefenseMode() {
        DefenseMode entered = service.enter(new DefenseModeEnterCommand(
                1L, "ACCIDENT_INJURY", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        assertEquals(DefenseModeStatus.ACTIVE, entered.getStatus());
        assertEquals(1_280_000L, entered.getReserveAmountSnapshot());
        assertEquals(3_400_000L, entered.getSafeAssetAmountSnapshot());
        assertEquals(4_680_000L, entered.getAvailableAssetsSnapshot());
        assertEquals(110_000L, entered.getDailyExpense());
        assertEquals(42, entered.getDDay());
        assertEquals(DefenseCalculationStatus.CALCULATED, entered.getCalculationStatus());

        DefenseMode released = service.release(entered.getDefenseId(),
                new DefenseModeReleaseCommand(1L, LocalDate.of(2026, 8, 8)));
        assertEquals(DefenseModeStatus.RELEASED, released.getStatus());
        assertEquals(LocalDate.of(2026, 8, 8), released.getReturnDate());
    }

    @Test
    void rejectsSecondActiveDefenseMode() {
        DefenseModeEnterCommand command = new DefenseModeEnterCommand(
                1L, "ILLNESS", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));
        service.enter(command);
        assertThrows(ServiceException.class, () -> service.enter(command));
    }

    @Test
    void recalculatesCurrentMonthDiagnosisBeforeImmediateActivation() {
        MemoryMapper immediateMapper = new MemoryMapper();
        AtomicReference<DefenseDiagnosisSnapshot> latestSnapshot = new AtomicReference<>(
                new DefenseDiagnosisSnapshot(null, null, null));
        AtomicReference<YearMonth> recalculatedMonth = new AtomicReference<>();
        Clock clock = clockAt(LocalDate.of(2026, 8, 21));
        DefenseModeService immediateService = new DefenseModeService(
                immediateMapper,
                userId -> latestSnapshot.get(),
                (userId, yearMonth) -> {
                    recalculatedMonth.set(yearMonth);
                    latestSnapshot.set(new DefenseDiagnosisSnapshot(1_200_000L, 600_000L, 3_000_000L));
                },
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                clock);

        DefenseMode entered = immediateService.enter(new DefenseModeEnterCommand(
                1L, "ILLNESS", LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 31)));

        assertEquals(YearMonth.of(2026, 8), recalculatedMonth.get());
        assertEquals(DefenseModeStatus.ACTIVE, entered.getStatus());
        assertEquals(1_800_000L, entered.getAvailableAssetsSnapshot());
        assertEquals(18, entered.getDDay());
    }

    @Test
    void schedulesFutureDefenseModeAndCalculatesSnapshotWhenActivated() {
        MemoryMapper scheduledMapper = new MemoryMapper();
        AtomicInteger recalculationCount = new AtomicInteger();
        AtomicReference<DefenseDiagnosisSnapshot> latestSnapshot = new AtomicReference<>(
                new DefenseDiagnosisSnapshot(100_000L, 200_000L, 900_000L));
        DefenseModeService scheduledService = new DefenseModeService(
                scheduledMapper,
                userId -> latestSnapshot.get(),
                (userId, yearMonth) -> recalculationCount.incrementAndGet(),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                clockAt(LocalDate.of(2026, 8, 3)));

        DefenseMode entered = scheduledService.enter(new DefenseModeEnterCommand(
                1L, "ILLNESS", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20)));

        assertEquals(DefenseModeStatus.SCHEDULED, entered.getStatus());
        assertEquals(null, entered.getAvailableAssetsSnapshot());
        assertEquals(null, entered.getDDay());
        assertEquals(0, recalculationCount.get());

        latestSnapshot.set(new DefenseDiagnosisSnapshot(1_200_000L, 600_000L, 3_000_000L));
        DefenseModeService activationService = new DefenseModeService(
                scheduledMapper,
                userId -> latestSnapshot.get(),
                (userId, yearMonth) -> recalculationCount.incrementAndGet(),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                clockAt(LocalDate.of(2026, 8, 10)));

        assertEquals(1, activationService.activateScheduledModes());
        assertEquals(1, recalculationCount.get());
        DefenseMode activated = scheduledMapper.findById(entered.getDefenseId());
        assertEquals(DefenseModeStatus.ACTIVE, activated.getStatus());
        assertEquals(1_800_000L, activated.getAvailableAssetsSnapshot());
        assertEquals(100_000L, activated.getDailyExpense());
        assertEquals(18, activated.getDDay());
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(ServiceException.class, () -> service.enter(new DefenseModeEnterCommand(
                1L, "OTHER", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 3))));
    }

    @Test
    void allowsEntryWithoutDiagnosisAndMarksCalculationUnavailable() {
        DefenseModeService serviceWithoutDiagnosis = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DefenseDiagnosisSnapshot(null, null, null),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Collections.emptyList());

        DefenseMode entered = serviceWithoutDiagnosis.enter(new DefenseModeEnterCommand(
                2L, "OTHER", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        assertEquals(DefenseCalculationStatus.DIAGNOSIS_REQUIRED, entered.getCalculationStatus());
        assertEquals(null, entered.getDDay());
    }

    @Test
    void calculatesFixedExpenseImpactAndKeepsUnknownLoanAmountUncalculated() {
        DefenseModeService fixedExpenseService = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DefenseDiagnosisSnapshot(1_280_000L, 3_400_000L, 3_300_000L),
                (userId, fromDate, toDate) -> Arrays.asList(
                        new FinancialCommitment(
                                1L, 10L, "SAVING_PAYMENT", "청년희망적금", null,
                                500_000L, null, null, LocalDate.of(2026, 8, 5), "CONFIRMED", "ESTIMATED"),
                        new FinancialCommitment(
                                2L, 20L, "LOAN_REPAYMENT", "신한 직장인 대출", 12_500_000L,
                                null, null, null, null, "INSUFFICIENT", "INSUFFICIENT"),
                        new FinancialCommitment(
                                3L, 30L, "INSURANCE_PREMIUM", "실비 보험", null,
                                100_000L, null, null, LocalDate.of(2026, 8, 7), "CONFIRMED", "CONFIRMED")),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                clockAt(LocalDate.of(2026, 8, 3)));
        DefenseMode entered = fixedExpenseService.enter(new DefenseModeEnterCommand(
                1L, "ACCIDENT_INJURY", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        FixedExpenseCheckSummary result = fixedExpenseService.getFixedExpenseCheck(entered);

        assertEquals(600_000L, result.getTotalExpectedAmount());
        assertEquals(3, result.getExpenses().size());
        assertEquals(38, result.getExpenses().get(0).getDDayAfter());
        assertEquals(4, result.getExpenses().get(0).getDDayReduction());
        assertEquals(FixedExpenseMaintainStatus.DIFFICULT,
                result.getExpenses().get(0).getMaintainStatus());
        assertEquals(null, result.getExpenses().get(1).getDDayAfter());
        assertEquals(12_500_000L, result.getExpenses().get(1).getOutstandingBalance());
        assertEquals(FixedExpenseMaintainStatus.UNDETERMINED,
                result.getExpenses().get(1).getMaintainStatus());
        assertEquals(FixedExpenseMaintainStatus.DIFFICULT,
                result.getExpenses().get(2).getMaintainStatus());
    }

    @Test
    void calculatesMaintainStatusFromAssetsAndPaymentImpactInsteadOfExpenseType() {
        DefenseModeService fixedExpenseService = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DefenseDiagnosisSnapshot(9_000_000L, 0L, 3_000_000L),
                (userId, fromDate, toDate) -> Arrays.asList(
                        new FinancialCommitment(
                                1L, 10L, "SAVING_PAYMENT", "소액 적금", null,
                                100_000L, null, null, LocalDate.of(2026, 8, 5), "CONFIRMED", "CONFIRMED"),
                        new FinancialCommitment(
                                2L, 20L, "SAVING_PAYMENT", "고액 적금", null,
                                8_000_000L, null, null, LocalDate.of(2026, 8, 5), "CONFIRMED", "CONFIRMED")),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                clockAt(LocalDate.of(2026, 8, 3)));
        DefenseMode entered = fixedExpenseService.enter(new DefenseModeEnterCommand(
                1L, "ACCIDENT_INJURY", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        FixedExpenseCheckSummary result = fixedExpenseService.getFixedExpenseCheck(entered);

        assertEquals(FixedExpenseMaintainStatus.NORMAL,
                result.getExpenses().get(0).getMaintainStatus());
        assertEquals(FixedExpenseMaintainStatus.REVIEW_SUSPENSION,
                result.getExpenses().get(1).getMaintainStatus());
    }

    @Test
    void calculatesCurrentDDayFromElapsedDaysWithoutChangingEntrySnapshot() {
        MemoryMapper currentMapper = new MemoryMapper();
        DefenseModeService currentService = new DefenseModeService(
                currentMapper,
                userId -> new DefenseDiagnosisSnapshot(1_280_000L, 3_400_000L, 3_300_000L),
                (userId, fromDate, toDate) -> Collections.singletonList(
                        new FinancialCommitment(
                                1L, 10L, "SAVING_PAYMENT", "청년희망적금", null,
                                500_000L, null, null, LocalDate.of(2026, 8, 15), "CONFIRMED", "CONFIRMED")),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                clockAt(LocalDate.of(2026, 8, 13)));
        DefenseMode entered = currentService.enter(new DefenseModeEnterCommand(
                1L, "ACCIDENT_INJURY", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 31)));

        assertEquals(32, currentService.getCurrentDDay(entered));
        assertEquals(42, entered.getDDay());
        assertEquals(42, currentMapper.findById(entered.getDefenseId()).getDDay());

        FixedExpenseCheckSummary result = currentService.getFixedExpenseCheck(entered);
        assertEquals(32, result.getExpenses().get(0).getDDayBefore());
        assertEquals(28, result.getExpenses().get(0).getDDayAfter());
        assertEquals(4, result.getExpenses().get(0).getDDayReduction());
    }

    @Test
    void calculatesExpectedIncomeLossForCurrentMonthDefensePeriod() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1);
        LocalDate endDate = today.withDayOfMonth(today.lengthOfMonth());
        DefenseModeService incomeLossService = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DefenseDiagnosisSnapshot(1_280_000L, 3_400_000L, 3_300_000L),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Arrays.asList(
                        new JobExpectedIncomeLoss(101L, "대리운전", 150_000L),
                        new JobExpectedIncomeLoss(102L, "배달라이더", 180_000L)));
        DefenseMode entered = incomeLossService.enter(new DefenseModeEnterCommand(
                1L, "ILLNESS", startDate, endDate));

        ExpectedIncomeLossSummary result = incomeLossService.getExpectedIncomeLoss(entered);

        assertEquals(330_000L, result.getTotalAmount());
        assertEquals(startDate, result.getPeriodStartDate());
        assertEquals(endDate, result.getPeriodEndDate());
        assertEquals("CALCULATED", result.getCalculationStatus());
        assertEquals(2, result.getJobs().size());
        assertEquals(150_000L, result.getJobs().get(0).getExpectedIncomeLoss());
    }

    private static class MemoryMapper implements DefenseModeMapper {
        private final Map<Long, DefenseMode> data = new HashMap<>();
        private long sequence;

        @Override
        public DefenseMode findById(Long defenseId) {
            return data.get(defenseId);
        }

        @Override
        public DefenseMode findActiveByUserId(Long userId) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()) && mode.getStatus() == DefenseModeStatus.ACTIVE)
                    .findFirst().orElse(null);
        }

        @Override
        public DefenseMode findCurrentByUserId(Long userId) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()))
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.ACTIVE
                            || mode.getStatus() == DefenseModeStatus.SCHEDULED)
                    .findFirst().orElse(null);
        }

        @Override
        public List<DefenseMode> findScheduledToActivate(LocalDate today) {
            return data.values().stream()
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.SCHEDULED)
                    .filter(mode -> !mode.getUnavailableStartDate().isAfter(today))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<DefenseMode> findCalendarPeriods(Long userId, LocalDate from, LocalDate to) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()))
                    .filter(mode -> !mode.getUnavailableStartDate().isAfter(to))
                    .filter(mode -> {
                        LocalDate endDate = mode.getStatus() == DefenseModeStatus.RELEASED
                                ? mode.getReturnDate()
                                : mode.getExpectedReturnDate();
                        return endDate != null && !endDate.isBefore(from);
                    })
                    .sorted(java.util.Comparator.comparing(DefenseMode::getUnavailableStartDate))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public int insert(DefenseMode defenseMode) {
            defenseMode.setDefenseId(++sequence);
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }

        @Override
        public int activate(DefenseMode defenseMode) {
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }

        @Override
        public int release(DefenseMode defenseMode) {
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }
    }

    private static Clock clockAt(LocalDate date) {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        return Clock.fixed(date.atStartOfDay(zoneId).toInstant(), zoneId);
    }
}
