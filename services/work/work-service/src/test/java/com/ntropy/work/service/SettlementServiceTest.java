package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.domain.UserScope;
import com.ntropy.work.client.holiday.HolidayApiClient;
import com.ntropy.work.config.SettlementBatchUserScopeProperties;
import com.ntropy.work.domain.entity.Holiday;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobPlatformMapping;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.InMemoryHolidayMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobPlatformMappingMapper;
import com.ntropy.work.mapper.InMemoryPlatformMapper;
import com.ntropy.work.mapper.InMemorySettlementMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;
import com.ntropy.work.mapper.InMemoryWorkLogPlatformIncomeMapper;
import com.ntropy.work.port.account.IncomingTransaction;
import com.ntropy.work.port.account.IncomingTransactionPort;
import com.ntropy.work.port.notification.NotificationPort;
import com.ntropy.work.port.notification.NotificationRequest;
import com.ntropy.work.port.user.UserPort;

class SettlementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_ID = 100L;
    private static final Long PLATFORM_ID = 10L;
    private static final LocalDate PROCESS_DATE = LocalDate.of(2026, 8, 10);
    private static final LocalDate PERIOD_DATE = PROCESS_DATE.minusDays(1); // DAILY offset=1

    private final InMemoryPlatformMapper platformMapper = new InMemoryPlatformMapper();
    private final InMemoryJobMapper jobMapper = new InMemoryJobMapper();
    private final InMemoryJobPlatformMappingMapper jobPlatformMappingMapper = new InMemoryJobPlatformMappingMapper();
    private final InMemoryWorkLogMapper workLogMapper = new InMemoryWorkLogMapper();
    private final InMemoryWorkLogPlatformIncomeMapper workLogPlatformIncomeMapper =
            new InMemoryWorkLogPlatformIncomeMapper(workLogMapper);
    private final InMemorySettlementMapper settlementMapper = new InMemorySettlementMapper();
    private final InMemoryHolidayMapper holidayMapperForTest = new InMemoryHolidayMapper();
    private final HolidayService holidayService =
            new HolidayService(new HolidayApiClient(null, null), holidayMapperForTest);
    private final StubUserPort userPort = new StubUserPort();
    private final StubNotificationPort notificationPort = new StubNotificationPort();
    private StubIncomingTransactionPort incomingTransactionPort;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        platformMapper.seed(Platform.builder()
                .platformId(PLATFORM_ID)
                .depositName("테스트플랫폼")
                .settlementCycle("DAILY")
                .settlementOffsetDay(1)
                .build());
        jobMapper.seed(Job.builder().jobId(JOB_ID).userId(USER_ID).isActive(true).build());
        incomingTransactionPort = new StubIncomingTransactionPort();
        service = new SettlementService(
                incomingTransactionPort, userPort,
                new SettlementBatchUserScopeProperties("REAL_ONLY"), platformMapper, jobMapper,
                jobPlatformMappingMapper, workLogMapper, workLogPlatformIncomeMapper, settlementMapper, holidayService,
                notificationPort);
    }

    @Test
    @DisplayName("입금 거래가 매칭되면 MATCHED SETTLEMENT가 생성되고 기간 내 CONFIRMED 로그만 COMPLETED로 갱신된다")
    void processSettlement_matched_createsSettlementAndUpdatesConfirmedLogsOnly() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        WorkLog inPeriodConfirmed = workLog(PERIOD_DATE, "CONFIRMED", 50_000L);
        WorkLog outOfPeriodConfirmed = workLog(PERIOD_DATE.minusDays(1), "CONFIRMED", 30_000L);
        WorkLog inPeriodPlanned = workLog(PERIOD_DATE, "PLANNED", 20_000L);
        workLogMapper.insert(inPeriodConfirmed);
        workLogMapper.insert(outOfPeriodConfirmed);
        workLogMapper.insert(inPeriodPlanned);
        insertIncome(inPeriodConfirmed, PLATFORM_ID, 50_000L, SettlementStatus.PENDING);
        incomingTransactionPort.transactions = List.of(transaction(999L, 48_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        Settlement settlement = settlements.get(0);
        assertEquals(SettlementMatchStatus.MATCHED, settlement.getStatus());
        assertEquals(USER_ID, settlement.getUserId());
        assertEquals(JOB_ID, settlement.getJobId());
        assertEquals(PERIOD_DATE, settlement.getPeriodStart());
        assertEquals(PERIOD_DATE, settlement.getPeriodEnd());
        assertEquals(PROCESS_DATE, settlement.getDepositDate());
        assertEquals(50_000L, settlement.getExpectedAmount());
        assertEquals(48_000L, settlement.getActualAmount());
        assertEquals(1, settlement.getTransactionCount());
        assertEquals(999L, settlement.getAccountTransactionId());

        assertEquals(SettlementStatus.COMPLETED, workLogMapper.findById(inPeriodConfirmed.getLogId()).getSettlementStatus());
        assertEquals(SettlementStatus.NONE, workLogMapper.findById(outOfPeriodConfirmed.getLogId()).getSettlementStatus());
        assertEquals(SettlementStatus.NONE, workLogMapper.findById(inPeriodPlanned.getLogId()).getSettlementStatus());
    }

    @Test
    @DisplayName("PLATFORM과 매칭되지 않는 거래는 job_id=null인 UNMATCHED SETTLEMENT로 합산 저장된다")
    void processSettlement_unmatchedTransaction_savesUnmatchedSettlement() {
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처", BigDecimal.valueOf(10_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        Settlement settlement = settlements.get(0);
        assertEquals(SettlementMatchStatus.UNMATCHED, settlement.getStatus());
        assertNull(settlement.getJobId());
        assertEquals(USER_ID, settlement.getUserId());
        assertEquals(10_000L, settlement.getActualAmount());
        assertEquals(1, settlement.getTransactionCount());
        assertEquals(PROCESS_DATE, settlement.getDepositDate());
    }

    @Test
    @DisplayName("같은 날짜의 UNMATCHED 거래 여러 건은 하나의 SETTLEMENT 행으로 합산된다")
    void processSettlement_multipleUnmatchedTransactionsSameDay_aggregatesIntoOneRow() {
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처1", BigDecimal.valueOf(10_000L)),
                new IncomingTransaction(2L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처2", BigDecimal.valueOf(5_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        assertEquals(15_000L, settlements.get(0).getActualAmount());
        assertEquals(2, settlements.get(0).getTransactionCount());
    }

    @Test
    @DisplayName("매칭되는 PLATFORM이지만 사용자가 JOB으로 등록하지 않았으면 UNMATCHED로 저장된다")
    void processSettlement_platformNotRegisteredAsJob_savesUnmatchedSettlement() {
        incomingTransactionPort.transactions = List.of(transaction(1L, 10_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        assertEquals(SettlementMatchStatus.UNMATCHED, settlements.get(0).getStatus());
    }

    @Test
    @DisplayName("이미 같은 accountTransactionId로 처리된 거래는 재처리하지 않는다 (배치 재실행 방지)")
    void processSettlement_sameTransactionAlreadySettled_skipsDuplicate() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        settlementMapper.insert(Settlement.builder()
                .userId(USER_ID).status(SettlementMatchStatus.MATCHED)
                .jobId(JOB_ID).periodStart(PERIOD_DATE).periodEnd(PERIOD_DATE)
                .depositDate(PROCESS_DATE)
                .expectedAmount(0L).actualAmount(48_000L).transactionCount(1).accountTransactionId(999L)
                .matchedAt(java.time.LocalDateTime.now())
                .build());
        incomingTransactionPort.transactions = List.of(transaction(999L, 48_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        assertEquals(1, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("같은 잡·같은 정산기간에 서로 다른 거래 2건이 들어오면 각각 SETTLEMENT로 반영된다")
    void processSettlement_twoDistinctTransactionsSamePeriod_bothCreateSettlements() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        incomingTransactionPort.transactions = List.of(
                transaction(111L, 30_000L),
                transaction(222L, 20_000L)
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(2, settlements.size());
        long totalActualAmount = settlements.stream().mapToLong(Settlement::getActualAmount).sum();
        assertEquals(50_000L, totalActualAmount);
    }

    @Test
    @DisplayName("같은 정산기간의 늦은 소득을 top-up하면 신규 소득만 expectedAmount에 반영된다")
    void processSettlement_topUpSamePeriod_doesNotDuplicateCompletedExpectedAmount() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        WorkLog first = workLog(PERIOD_DATE, "CONFIRMED", 40_000L);
        WorkLog second = workLog(PERIOD_DATE, "CONFIRMED", 10_000L);
        workLogMapper.insert(first);
        workLogMapper.insert(second);
        insertIncome(first, PLATFORM_ID, 40_000L, SettlementStatus.PENDING);
        insertIncome(second, PLATFORM_ID, 10_000L, SettlementStatus.PENDING);

        incomingTransactionPort.transactions = List.of(transaction(111L, 50_000L));
        service.processSettlement(USER_ID, PROCESS_DATE);

        WorkLog late = workLog(PERIOD_DATE, "CONFIRMED", 20_000L);
        workLogMapper.insert(late);
        insertIncome(late, PLATFORM_ID, 20_000L, SettlementStatus.PENDING);
        incomingTransactionPort.transactions = List.of(transaction(222L, 20_000L));
        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(2, settlements.size());
        Settlement initial = settlements.stream()
                .filter(settlement -> settlement.getAccountTransactionId().equals(111L))
                .findFirst()
                .orElseThrow();
        Settlement topUp = settlements.stream()
                .filter(settlement -> settlement.getAccountTransactionId().equals(222L))
                .findFirst()
                .orElseThrow();
        assertEquals(50_000L, initial.getExpectedAmount());
        assertEquals(20_000L, topUp.getExpectedAmount());
        assertEquals(70_000L, settlements.stream().mapToLong(Settlement::getExpectedAmount).sum());
        assertEquals(70_000L, settlements.stream().mapToLong(Settlement::getActualAmount).sum());
        assertEquals(SettlementStatus.COMPLETED,
                workLogPlatformIncomeMapper.findByLogId(late.getLogId()).get(0).getSettlementStatus());
    }

    @Test
    @DisplayName("이미 같은 날짜의 UNMATCHED SETTLEMENT가 있으면 중복 생성하지 않는다")
    void processSettlement_alreadyUnmatchedSettled_skipsDuplicate() {
        settlementMapper.insert(Settlement.builder()
                .userId(USER_ID).status(SettlementMatchStatus.UNMATCHED)
                .jobId(null).periodStart(PROCESS_DATE).periodEnd(PROCESS_DATE)
                .depositDate(PROCESS_DATE)
                .expectedAmount(0L).actualAmount(5_000L).transactionCount(1).accountTransactionId(null)
                .matchedAt(java.time.LocalDateTime.now())
                .build());
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처", BigDecimal.valueOf(10_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        assertEquals(1, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("ON_DEMAND 플랫폼 거래는 근무일지 매칭 없이 해당 잡으로 정산 처리된다")
    void processSettlement_onDemandPlatform_settlesToJobWithoutTouchingWorkLog() {
        Long onDemandPlatformId = 20L;
        platformMapper.seed(Platform.builder()
                .platformId(onDemandPlatformId)
                .depositName("카카오모빌리티")
                .settlementCycle("DAILY")
                .settlementTriggerType("ON_DEMAND")
                .build());
        jobPlatformMappingMapper.insert(
                JobPlatformMapping.builder().jobId(JOB_ID).platformId(onDemandPlatformId).build());
        // WorkLog 확정 시점에 이미 WorkLogService가 즉시 COMPLETED로 처리해뒀다고 가정 - 이 배치가
        // 근무일지를 다시 참조/변경하지 않는지가 이 테스트의 핵심이다.
        WorkLog alreadyCompletedLog = workLog(PERIOD_DATE, "CONFIRMED", 50_000L);
        alreadyCompletedLog.setSettlementStatus(SettlementStatus.COMPLETED);
        workLogMapper.insert(alreadyCompletedLog);
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "카카오모빌리티", BigDecimal.valueOf(120_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        Settlement settlement = settlements.get(0);
        assertEquals(SettlementMatchStatus.MATCHED, settlement.getStatus());
        assertEquals(JOB_ID, settlement.getJobId());
        assertEquals(120_000L, settlement.getActualAmount());
        assertEquals(PROCESS_DATE, settlement.getPeriodStart());
        assertEquals(PROCESS_DATE, settlement.getPeriodEnd());
        assertEquals(1L, settlement.getAccountTransactionId());
        // 이 배치는 근무일지를 건드리지 않는다 - 이미 COMPLETED였던 상태 그대로 유지된다.
        assertEquals(SettlementStatus.COMPLETED, workLogMapper.findById(alreadyCompletedLog.getLogId()).getSettlementStatus());
    }

    @Test
    @DisplayName("BUSINESS_DAY 플랫폼은 공휴일까지 건너뛰어 workDate를 역산하고, 그 공휴일에 일한 근무일지도 같은 기간으로 매칭한다")
    void processSettlement_businessDayPlatform_skipsInjectedHolidayAndCoversItInPeriod() {
        Long businessDayPlatformId = 40L;
        LocalDate paymentDate = LocalDate.of(2026, 8, 17); // 월요일
        LocalDate holiday = LocalDate.of(2026, 8, 14); // 금요일 - 공휴일로 주입
        LocalDate expectedWorkDate = LocalDate.of(2026, 8, 13); // 목요일 - 공휴일까지 건너뛴 역산 결과
        LocalDate expectedPeriodEnd = LocalDate.of(2026, 8, 16); // 일요일 - 다음 영업일(월, 8/17) 직전까지 확장

        platformMapper.seed(Platform.builder()
                .platformId(businessDayPlatformId)
                .depositName("배민커넥트테스트")
                .settlementCycle("DAILY")
                .settlementOffsetDay(1)
                .settlementOffsetUnit("BUSINESS_DAY")
                .build());
        jobPlatformMappingMapper.insert(
                JobPlatformMapping.builder().jobId(JOB_ID).platformId(businessDayPlatformId).build());
        holidayMapperForTest.seed(Holiday.builder()
                .holidayDate(holiday).name("임시공휴일").build());

        WorkLog thursdayLog = workLog(expectedWorkDate, "CONFIRMED", 40_000L);
        // 공휴일(8/14, 금)에 실제로 일한 근무일지 - 기간이 확장되면서 같이 매칭돼야 한다
        WorkLog holidayWorkLog = workLog(holiday, "CONFIRMED", 25_000L);
        workLogMapper.insert(thursdayLog);
        workLogMapper.insert(holidayWorkLog);
        insertIncome(thursdayLog, businessDayPlatformId, 40_000L, SettlementStatus.PENDING);
        insertIncome(holidayWorkLog, businessDayPlatformId, 25_000L, SettlementStatus.PENDING);
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, paymentDate, LocalTime.NOON, "배민커넥트테스트", BigDecimal.valueOf(65_000L))
        );

        service.processSettlement(USER_ID, paymentDate);

        Settlement settlement = settlementMapper.findAll().get(0);
        assertEquals(expectedWorkDate, settlement.getPeriodStart());
        assertEquals(expectedPeriodEnd, settlement.getPeriodEnd());
        assertEquals(65_000L, settlement.getExpectedAmount());
        assertEquals(SettlementStatus.COMPLETED, workLogMapper.findById(thursdayLog.getLogId()).getSettlementStatus());
        assertEquals(SettlementStatus.COMPLETED, workLogMapper.findById(holidayWorkLog.getLogId()).getSettlementStatus());
    }

    @Test
    @DisplayName("여러 플랫폼을 동시에 뛴 근무일지는 그중 한 플랫폼만 매칭돼도 WorkLog가 PARTIAL이 된다")
    void processSettlement_multiPlatformWorkLog_partiallyMatched_becomesPartial() {
        Long platformBId = 50L;
        platformMapper.seed(Platform.builder()
                .platformId(platformBId)
                .depositName("두번째플랫폼")
                .settlementCycle("DAILY")
                .settlementOffsetDay(1)
                .build());
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(platformBId).build());

        WorkLog multiPlatformLog = workLog(PERIOD_DATE, "CONFIRMED", 40_000L);
        workLogMapper.insert(multiPlatformLog);
        insertIncome(multiPlatformLog, PLATFORM_ID, 25_000L, SettlementStatus.PENDING);
        insertIncome(multiPlatformLog, platformBId, 15_000L, SettlementStatus.PENDING);

        // PLATFORM_ID 몫만 입금됨 - platformBId 몫은 아직
        incomingTransactionPort.transactions = List.of(transaction(999L, 25_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        assertEquals(SettlementStatus.PARTIAL, workLogMapper.findById(multiPlatformLog.getLogId()).getSettlementStatus());
    }

    @Test
    @DisplayName("runDailyBatch는 활성 사용자 전원에 대해 최근 BACKFILL_DAYS일씩 정산을 재확인한다")
    void runDailyBatch_processesActiveUsersAndRecentDays() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        userPort.userIds = List.of(USER_ID);
        LocalDate today = LocalDate.now();
        WorkLog logForYesterday = workLog(today.minusDays(2), "CONFIRMED", 48_000L); // offset=1이라 today-1 입금 시 today-2가 근무일
        workLogMapper.insert(logForYesterday);
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(555L, today.minusDays(1), LocalTime.NOON, "테스트플랫폼", BigDecimal.valueOf(48_000L))
        );

        service.runDailyBatch();

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        assertEquals(555L, settlements.get(0).getAccountTransactionId());
    }

    @Test
    @DisplayName("runDailyBatch는 활성 사용자가 없으면 아무것도 처리하지 않는다")
    void runDailyBatch_noActiveUsers_doesNothing() {
        userPort.userIds = List.of();

        service.runDailyBatch();

        assertEquals(0, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("설정된 batch.settlement.user-scope를 UserPort에 그대로 전달한다")
    void runDailyBatch_passesConfiguredUserScopeToClient() {
        service.runDailyBatch();

        assertEquals(UserScope.REAL_ONLY, userPort.lastRequestedScope);
    }

    @Test
    @DisplayName("runDailyBatch에서 여러 날짜의 정산이 생성돼도 유저당 정산 완료 알림은 1건만 발송되고, 건수/총액이 합산되어 본문에 담긴다")
    void runDailyBatch_settlementCreatedOnMultipleDays_sendsSingleNotificationPerUser() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        userPort.userIds = List.of(USER_ID);
        LocalDate today = LocalDate.now();
        // offset=1이라 today, today-1 각각 입금되면 today-1, today-2가 근무일
        workLogMapper.insert(workLog(today.minusDays(1), "CONFIRMED", 40_000L));
        workLogMapper.insert(workLog(today.minusDays(2), "CONFIRMED", 48_000L));
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(111L, today, LocalTime.NOON, "테스트플랫폼", BigDecimal.valueOf(40_000L)),
                new IncomingTransaction(555L, today.minusDays(1), LocalTime.NOON, "테스트플랫폼", BigDecimal.valueOf(48_000L))
        );

        service.runDailyBatch();

        assertEquals(2, settlementMapper.findAll().size());
        assertEquals(1, notificationPort.sent.size());
        NotificationRequest notification = notificationPort.sent.get(0);
        assertEquals(USER_ID, notification.userId());
        assertEquals("settlement-completed-" + USER_ID + "-" + today, notification.eventId());
        assertEquals("오늘 정산 2건, 총 88,000원이 처리됐어요.", notification.body());
    }

    @Test
    @DisplayName("processSettlementDetailed는 생성된 정산 건수와 실입금액 총합을 반환한다")
    void processSettlementDetailed_returnsCreatedCountAndTotalAmount() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        incomingTransactionPort.transactions = List.of(
                transaction(111L, 30_000L),
                transaction(222L, 20_000L)
        );

        SettlementService.SettlementBatchOutcome outcome =
                service.processSettlementDetailed(USER_ID, PROCESS_DATE);

        assertEquals(2, outcome.createdCount());
        assertEquals(50_000L, outcome.totalAmount());
    }

    @Test
    @DisplayName("UNMATCHED 거래만 있으면 processSettlementDetailed의 건수/총액은 0이다")
    void processSettlementDetailed_onlyUnmatched_returnsZero() {
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처", BigDecimal.valueOf(10_000L))
        );

        SettlementService.SettlementBatchOutcome outcome =
                service.processSettlementDetailed(USER_ID, PROCESS_DATE);

        assertEquals(0, outcome.createdCount());
        assertEquals(0L, outcome.totalAmount());
    }

    @Test
    @DisplayName("UNMATCHED 거래만 있으면 정산 완료 알림을 보내지 않는다")
    void runDailyBatch_onlyUnmatchedTransactions_doesNotSendNotification() {
        userPort.userIds = List.of(USER_ID);
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(1L, LocalDate.now(), LocalTime.NOON, "알수없는입금처", BigDecimal.valueOf(10_000L))
        );

        service.runDailyBatch();

        assertEquals(0, notificationPort.sent.size());
    }

    @Test
    @DisplayName("이미 처리된 거래만 재확인되면(백필 재실행) 알림을 다시 보내지 않는다")
    void runDailyBatch_onlyAlreadyProcessedTransactions_doesNotResendNotification() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        userPort.userIds = List.of(USER_ID);
        LocalDate today = LocalDate.now();
        settlementMapper.insert(Settlement.builder()
                .userId(USER_ID).status(SettlementMatchStatus.MATCHED)
                .jobId(JOB_ID).periodStart(today).periodEnd(today)
                .depositDate(today)
                .expectedAmount(0L).actualAmount(40_000L).transactionCount(1).accountTransactionId(999L)
                .matchedAt(java.time.LocalDateTime.now())
                .build());
        incomingTransactionPort.transactions = List.of(
                new IncomingTransaction(999L, today, LocalTime.NOON, "테스트플랫폼", BigDecimal.valueOf(40_000L))
        );

        service.runDailyBatch();

        assertEquals(1, settlementMapper.findAll().size());
        assertEquals(0, notificationPort.sent.size());
    }

    private static WorkLog workLog(LocalDate workDate, String status, long estimatedIncome) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(workDate)
                .estimatedIncome(estimatedIncome)
                .status(status)
                .fatigue(0L)
                .settlementStatus(SettlementStatus.NONE)
                .build();
    }

    private void insertIncome(WorkLog workLog, Long platformId, long amount, SettlementStatus status) {
        workLogPlatformIncomeMapper.insert(WorkLogPlatformIncome.builder()
                .logId(workLog.getLogId())
                .platformId(platformId)
                .expectedAmount(amount)
                .settlementStatus(status)
                .build());
    }

    private static IncomingTransaction transaction(long transactionId, long amount) {
        return new IncomingTransaction(
                transactionId, PROCESS_DATE, LocalTime.NOON, "테스트플랫폼", BigDecimal.valueOf(amount));
    }

    private static final class StubIncomingTransactionPort implements IncomingTransactionPort {
        private List<IncomingTransaction> transactions = List.of();

        @Override
        public List<IncomingTransaction> findIncomingTransactions(
                Long userId, LocalDate startDate, LocalDate endDate) {
            return transactions;
        }
    }

    private static final class StubUserPort implements UserPort {
        private List<Long> userIds = List.of();
        private UserScope lastRequestedScope;

        @Override
        public List<Long> findActiveUserIds(UserScope scope) {
            this.lastRequestedScope = scope;
            return userIds;
        }
    }

    private static final class StubNotificationPort implements NotificationPort {
        private final List<NotificationRequest> sent = new java.util.ArrayList<>();

        @Override
        public void notify(NotificationRequest request) {
            sent.add(request);
        }
    }
}
