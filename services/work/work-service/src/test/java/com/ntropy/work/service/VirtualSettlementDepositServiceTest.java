package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ntropy.work.config.SettlementBatchUserScopeProperties;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.mapper.projection.VirtualSettlementIncome;
import com.ntropy.work.port.account.SettlementDepositOutcome;
import com.ntropy.work.port.account.SettlementDepositPort;
import com.ntropy.work.port.account.SettlementDepositRequest;
import com.ntropy.work.port.user.UserPort;

class VirtualSettlementDepositServiceTest {

    private final StubIncomeMapper incomeMapper = new StubIncomeMapper();
    private final StubPlatformMapper platformMapper = new StubPlatformMapper();
    private final RecordingDepositPort depositPort = new RecordingDepositPort();
    private final VirtualSettlementDepositService service = new VirtualSettlementDepositService(
            scope -> List.of(1L),
            new SettlementBatchUserScopeProperties("ALL"),
            incomeMapper,
            platformMapper,
            new StubHolidayService(Set.of()),
            depositPort
    );

    @Test
    void groupsExpectedIncomeByPlatformSettlementPeriod() {
        platformMapper.platforms = List.of(
                platform(1L, "배민", "DAILY", "AUTO", 3, "BUSINESS_DAY", null, null),
                platform(2L, "쿠팡이츠정산", "WEEKLY", "AUTO", 3, "BUSINESS_DAY", "FRI", null)
        );
        incomeMapper.pending = List.of(
                income(1L, LocalDate.of(2026, 7, 16), 40_000L, SettlementStatus.PENDING),
                income(2L, LocalDate.of(2026, 7, 16), 30_000L, SettlementStatus.PENDING),
                income(2L, LocalDate.of(2026, 7, 20), 20_000L, SettlementStatus.PENDING)
        );

        VirtualSettlementDepositBatchResult result = service.processUser(1L, LocalDate.of(2026, 7, 24));

        assertEquals(2, result.createdCount());
        assertEquals(2, result.matchTargets().size());
        assertEquals(2, depositPort.requests.size());
        SettlementDepositRequest baemin = depositPort.requests.get(0);
        assertEquals(LocalDate.of(2026, 7, 21), baemin.depositDate());
        assertEquals(40_000L, baemin.amount());
        SettlementDepositRequest coupang = depositPort.requests.get(1);
        assertEquals(LocalDate.of(2026, 7, 24), coupang.depositDate());
        assertEquals(LocalDate.of(2026, 7, 15), coupang.periodStart());
        assertEquals(LocalDate.of(2026, 7, 21), coupang.periodEnd());
        assertEquals(50_000L, coupang.amount());
    }

    @Test
    void skipsFutureAndOnDemandSettlements() {
        platformMapper.platforms = List.of(
                platform(1L, "배민", "DAILY", "AUTO", 3, "BUSINESS_DAY", null, null),
                platform(4L, "카카오모빌리티", "DAILY", "ON_DEMAND", 1, "CALENDAR_DAY", null, null)
        );
        incomeMapper.pending = List.of(
                income(1L, LocalDate.of(2026, 7, 16), 40_000L, SettlementStatus.PENDING),
                income(4L, LocalDate.of(2026, 7, 16), 30_000L, SettlementStatus.COMPLETED)
        );

        VirtualSettlementDepositBatchResult result = service.processUser(1L, LocalDate.of(2026, 7, 20));

        assertEquals(0, result.createdCount());
        assertEquals(0, depositPort.requests.size());
    }

    @Test
    void includesCompletedIncomeInCumulativeTargetWhenLateIncomeIsPending() {
        platformMapper.platforms = List.of(
                platform(2L, "쿠팡이츠정산", "WEEKLY", "AUTO", 3, "BUSINESS_DAY", "FRI", null)
        );
        incomeMapper.pending = List.of(
                income(2L, LocalDate.of(2026, 7, 16), 50_000L, SettlementStatus.COMPLETED),
                income(2L, LocalDate.of(2026, 7, 20), 20_000L, SettlementStatus.PENDING)
        );

        VirtualSettlementDepositBatchResult result = service.processUser(1L, LocalDate.of(2026, 7, 30));

        assertEquals(1, result.createdCount());
        assertEquals(70_000L, depositPort.requests.get(0).amount());
        assertEquals(LocalDate.of(2026, 7, 24), result.matchTargets().get(0).depositDate());
    }

    @Test
    void invalidPlatformRuleDoesNotBlockOtherPlatform() {
        platformMapper.platforms = List.of(
                platform(1L, "배민", "DAILY", "AUTO", 3, "BUSINESS_DAY", null, null),
                platform(99L, "오류플랫폼", null, "AUTO", 1, "CALENDAR_DAY", null, null)
        );
        incomeMapper.pending = List.of(
                income(99L, LocalDate.of(2026, 7, 16), 10_000L, SettlementStatus.PENDING),
                income(1L, LocalDate.of(2026, 7, 16), 40_000L, SettlementStatus.PENDING)
        );

        VirtualSettlementDepositBatchResult result = service.processUser(1L, LocalDate.of(2026, 7, 24));

        assertEquals(1, result.createdCount());
        assertEquals(1, depositPort.requests.size());
        assertEquals(1L, depositPort.requests.get(0).platformId());
    }

    @Test
    void returnsPastMatchTargetEvenWhenDepositWasAlreadyGenerated() {
        platformMapper.platforms = List.of(
                platform(1L, "배민", "DAILY", "AUTO", 3, "BUSINESS_DAY", null, null)
        );
        incomeMapper.pending = List.of(
                income(1L, LocalDate.of(2026, 6, 1), 40_000L, SettlementStatus.PENDING)
        );
        depositPort.nextResult = new SettlementDepositOutcome(true, false);

        VirtualSettlementDepositBatchResult result = service.processUser(1L, LocalDate.of(2026, 7, 24));

        assertEquals(0, result.createdCount());
        assertEquals(1, result.matchTargets().size());
        assertEquals(LocalDate.of(2026, 6, 4), result.matchTargets().get(0).depositDate());
    }

    private static VirtualSettlementIncome income(
            Long platformId, LocalDate workDate, Long amount, SettlementStatus status
    ) {
        VirtualSettlementIncome income = new VirtualSettlementIncome();
        income.setIncomeId(platformId);
        income.setUserId(1L);
        income.setPlatformId(platformId);
        income.setWorkDate(workDate);
        income.setExpectedAmount(amount);
        income.setSettlementStatus(status);
        return income;
    }

    private static Platform platform(Long id, String depositName, String cycle, String trigger,
                                     Integer offset, String unit, String dayOfWeek, Integer dayOfMonth) {
        return Platform.builder()
                .platformId(id)
                .depositName(depositName)
                .settlementCycle(cycle)
                .settlementTriggerType(trigger)
                .settlementOffsetDay(offset)
                .settlementOffsetUnit(unit)
                .settlementDayOfWeek(dayOfWeek)
                .settlementDayOfMonth(dayOfMonth)
                .build();
    }

    private static final class StubIncomeMapper implements WorkLogPlatformIncomeMapper {
        private List<VirtualSettlementIncome> pending = List.of();

        @Override public void insert(WorkLogPlatformIncome income) { }
        @Override public void update(WorkLogPlatformIncome income) { }
        @Override public List<WorkLogPlatformIncome> findByLogId(Long logId) { return List.of(); }
        @Override public void deleteByLogId(Long logId) { }
        @Override public List<WorkLogPlatformIncome> findConfirmedByJobIdAndPlatformIdAndDateRange(
                Long jobId, Long platformId, LocalDate startDate, LocalDate endDate) { return List.of(); }
        @Override public List<WorkLogPlatformIncome> findConfirmedByUserIdAndDateRange(
                Long userId, LocalDate startDate, LocalDate endDate) { return List.of(); }
        @Override public List<WorkLogPlatformIncome> findConfirmedByUserIdInAndDateRange(
                List<Long> userIds, LocalDate startDate, LocalDate endDate) { return List.of(); }
        @Override public List<VirtualSettlementIncome> findConfirmedByUserIdUpToDateForVirtualSettlement(
                Long userId, LocalDate endDate) { return pending; }
    }

    private static final class StubPlatformMapper implements PlatformMapper {
        private List<Platform> platforms = List.of();

        @Override public void insert(Platform platform) { }
        @Override public Platform findById(Long platformId) {
            return platforms.stream().filter(platform -> platformId.equals(platform.getPlatformId())).findFirst().orElse(null);
        }
        @Override public List<Platform> findAll() { return platforms; }
        @Override public void update(Platform platform) { }
        @Override public void deleteById(Long platformId) { }
    }

    private static final class StubHolidayService extends HolidayService {
        private final Set<LocalDate> holidays;

        private StubHolidayService(Set<LocalDate> holidays) {
            super(null, null);
            this.holidays = holidays;
        }

        @Override
        public Set<LocalDate> getHolidays(LocalDate startDate, LocalDate endDate) {
            return holidays;
        }
    }

    private static final class RecordingDepositPort implements SettlementDepositPort {
        private final List<SettlementDepositRequest> requests = new ArrayList<>();
        private SettlementDepositOutcome nextResult = new SettlementDepositOutcome(true, true);

        @Override
        public SettlementDepositOutcome createOrAdjust(SettlementDepositRequest request) {
            requests.add(request);
            return nextResult;
        }
    }
}
