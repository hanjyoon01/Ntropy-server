package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ntropy.work.config.SettlementBatchUserScopeProperties;
import com.ntropy.work.domain.PlatformMatchResult;
import com.ntropy.work.domain.PlatformMatcher;
import com.ntropy.work.domain.SettlementPeriod;
import com.ntropy.work.domain.SettlementPeriodCalculator;
import com.ntropy.work.domain.WorkLogSettlementStatusCalculator;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.entity.WorkLogPlatformIncome;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.JobPlatformMappingMapper;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.port.account.IncomingTransaction;
import com.ntropy.work.port.account.IncomingTransactionPort;
import com.ntropy.work.port.notification.NotificationPort;
import com.ntropy.work.port.notification.NotificationRequest;
import com.ntropy.work.port.user.UserPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 입금 거래를 PLATFORM/JOB과 매칭해 SETTLEMENT를 생성하고, 해당 기간의 확정된(CONFIRMED)
 * WORK_LOG를 COMPLETED로 갱신한다. processDate 당일의 입금 거래만 확인하므로 매일 배치로
 * 호출하는 것을 전제로 한다. 이미 같은 accountTransactionId로 처리된 거래는 재처리하지
 * 않는다 - 배치가 중복 실행돼도 안전하고, 같은 잡·같은 정산기간에 서로 다른 거래가 여러 건
 * 들어와도(거래 ID가 다르므로) 각각 정상적으로 반영된다.
 * 각 SETTLEMENT의 expectedAmount는 기간 전체 누계가 아니라 해당 거래가 새로 COMPLETED로
 * 전환한 PENDING 소득의 합계다. 따라서 같은 기간의 top-up 거래도 기존 완료 소득을 중복 집계하지 않는다.
 *
 * <p>매칭되지 않은(UNMATCHED) 거래도 버리지 않고, 같은 날짜에 들어온 것들을 합산해
 * status=UNMATCHED(job_id=null) 행 하나로 저장한다. 한 플랫폼에 회원 잡이 여러 개
 * 매핑되는 경우(AMBIGUOUS)는 없다고 가정하고 별도로 다루지 않는다.</p>
 *
 * <p>ON_DEMAND(포인트 적립 후 사용자가 임의 시점에 출금 신청) platform은 실제 입금액이
 * 특정 근무일과 대응되지 않으므로, 매칭돼도 WorkLog는 건드리지 않고 해당 잡으로 SETTLEMENT만
 * 남긴다. WorkLog.settlementStatus는 확정(CONFIRMED) 시점에 WorkLogService가 이미
 * 즉시 COMPLETED로 처리한다 - 이 배치와는 독립적이다.</p>
 *
 * <p>settlement_offset_unit이 BUSINESS_DAY인 platform(배민커넥트/쿠팡이츠 배달파트너)만
 * HolidayService로 공휴일을 조회해 정산 기간 계산에 반영한다. CALENDAR_DAY 플랫폼은 공휴일
 * 조회 자체를 안 해서 불필요한 DB 조회를 피한다.</p>
 *
 * <p>실제 트리거는 SettlementScheduler가 매일 {@link #runDailyBatch()}를 호출하는 방식이다.
 * account-service의 Codef 동기화 배치가 먼저 끝나야 그날 거래가 조회되므로, 스케줄 시각은
 * 그 배치 완료 이후로 잡아야 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    /** BUSINESS_DAY 역산 시 조회할 공휴일 범위 버퍼(일). offset이 커져도 여유 있게 잡음. */
    private static final int HOLIDAY_LOOKBACK_DAYS = 30;
    private static final String BUSINESS_DAY = "BUSINESS_DAY";
    /** 배치 실행일 기준으로 매번 재확인할 최근 일수. Codef 배치가 늦게 끝나거나 실패해도
     *  다음날 배치가 자동으로 따라잡도록 하기 위함 - accountTransactionId 중복 방지가
     *  이미 있어 같은 날짜를 여러 번 재처리해도 안전하다. */
    private static final int BACKFILL_DAYS = 3;

    private final IncomingTransactionPort incomingTransactionPort;
    private final UserPort userPort;
    private final SettlementBatchUserScopeProperties userScopeProperties;
    private final PlatformMapper platformMapper;
    private final JobMapper jobMapper;
    private final JobPlatformMappingMapper jobPlatformMappingMapper;
    private final WorkLogMapper workLogMapper;
    private final WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;
    private final SettlementMapper settlementMapper;
    private final HolidayService holidayService;
    private final NotificationPort notificationPort;

    /**
     * 전체 활성 사용자를 대상으로, 오늘부터 최근 BACKFILL_DAYS일을 매번 재확인하는 배치.
     * 스케줄러(SettlementScheduler)가 매일 호출하는 것을 전제로 한다. 한 사용자 처리 중
     * 예외가 나도 다른 사용자 처리에 영향을 주지 않도록 사용자 단위로 예외를 격리한다.
     */
    public void runDailyBatch() {
        List<Long> userIds = userPort.findActiveUserIds(userScopeProperties.getUserScope());
        if (userIds == null || userIds.isEmpty()) {
            log.info("[정산 배치] 대상 사용자가 없습니다.");
            return;
        }

        LocalDate today = LocalDate.now();
        for (Long userId : userIds) {
            int totalCount = 0;
            long totalAmount = 0L;
            for (int i = 0; i < BACKFILL_DAYS; i++) {
                LocalDate processDate = today.minusDays(i);
                try {
                    SettlementBatchOutcome outcome = processSettlementDetailed(userId, processDate);
                    totalCount += outcome.createdCount();
                    totalAmount += outcome.totalAmount();
                } catch (Exception e) {
                    log.error("[정산 배치] 사용자 처리 실패. userId={}, processDate={}", userId, processDate, e);
                }
            }
            if (totalCount > 0) {
                notifySettlementCompleted(userId, today, totalCount, totalAmount);
            }
        }
    }

    /** 이번 호출에서 SETTLEMENT가 새로 생성됐으면 true. runDailyBatch가 유저당 알림 발송 여부를 판단하는 데 사용한다. */
    public boolean processSettlement(Long userId, LocalDate processDate) {
        return processSettlementDetailed(userId, processDate).createdCount() > 0;
    }

    /** 유저의 특정 날짜 정산 처리 결과. 생성된 SETTLEMENT 건수와 실입금액 총합을 담아 알림 내용 구체화에 쓴다. */
    public record SettlementBatchOutcome(int createdCount, long totalAmount) {
        private static final SettlementBatchOutcome EMPTY = new SettlementBatchOutcome(0, 0L);
    }

    public SettlementBatchOutcome processSettlementDetailed(Long userId, LocalDate processDate) {
        List<IncomingTransaction> transactions =
                incomingTransactionPort.findIncomingTransactions(userId, processDate, processDate);
        if (transactions.isEmpty()) {
            return SettlementBatchOutcome.EMPTY;
        }

        List<Platform> platforms = platformMapper.findAll();
        List<Job> jobs = jobMapper.findByUserId(userId).stream()
                .filter(job -> Boolean.TRUE.equals(job.getIsActive()))
                .toList();

        int createdCount = 0;
        long createdAmount = 0L;
        long unmatchedAmount = 0;
        int unmatchedCount = 0;
        for (IncomingTransaction transaction : transactions) {
            TransactionResult result = processMatchedTransaction(userId, transaction, platforms, jobs);
            if (result.outcome() == MatchOutcome.UNMATCHED) {
                unmatchedAmount += transaction.amount().longValueExact();
                unmatchedCount++;
            } else if (result.outcome() == MatchOutcome.CREATED) {
                createdCount++;
                createdAmount += result.amount();
            }
        }
        if (unmatchedCount > 0) {
            saveUnmatchedSettlement(userId, processDate, unmatchedAmount, unmatchedCount);
        }
        return new SettlementBatchOutcome(createdCount, createdAmount);
    }

    /** processMatchedTransaction의 결과. 이미 처리된 거래(ALREADY_PROCESSED)는 알림 대상에서 제외하기 위해 CREATED와 구분한다. */
    private enum MatchOutcome {
        UNMATCHED, ALREADY_PROCESSED, CREATED
    }

    /** processMatchedTransaction 결과. amount는 CREATED일 때만 의미 있고(정산 실입금액), 그 외엔 0이다. */
    private record TransactionResult(MatchOutcome outcome, long amount) {
    }

    private TransactionResult processMatchedTransaction(Long userId, IncomingTransaction transaction,
                                                     List<Platform> platforms, List<Job> jobs) {
        PlatformMatchResult result = PlatformMatcher.match(transaction.counterpartyName(), platforms);
        if (!(result instanceof PlatformMatchResult.Matched matched)) {
            return new TransactionResult(MatchOutcome.UNMATCHED, 0L);
        }
        Platform platform = matched.platform();
        Long jobId = resolveJobId(platform.getPlatformId(), jobs);
        if (jobId == null) {
            return new TransactionResult(MatchOutcome.UNMATCHED, 0L);
        }

        if (settlementMapper.existsByAccountTransactionId(transaction.transactionId())) {
            return new TransactionResult(MatchOutcome.ALREADY_PROCESSED, 0L);
        }

        long transactionAmount = transaction.amount().longValueExact();

        if ("ON_DEMAND".equals(platform.getSettlementTriggerType())) {
            saveOnDemandSettlement(userId, jobId, transaction);
            return new TransactionResult(MatchOutcome.CREATED, transactionAmount);
        }

        Set<LocalDate> holidays = BUSINESS_DAY.equals(platform.getSettlementOffsetUnit())
                ? holidayService.getHolidays(
                        transaction.transactionDate().minusDays(HOLIDAY_LOOKBACK_DAYS), transaction.transactionDate())
                : Set.of();
        SettlementPeriod period = SettlementPeriodCalculator.calculate(
                platform, transaction.transactionDate(), holidays);
        List<WorkLogPlatformIncome> incomesInPeriod = workLogPlatformIncomeMapper
                .findConfirmedByJobIdAndPlatformIdAndDateRange(
                        jobId, platform.getPlatformId(), period.start(), period.end());
        List<WorkLogPlatformIncome> newlyCoveredIncomes = incomesInPeriod.stream()
                .filter(income -> income.getSettlementStatus() == SettlementStatus.PENDING)
                .toList();
        long expectedAmount = newlyCoveredIncomes.stream()
                .mapToLong(income -> income.getExpectedAmount() == null ? 0L : income.getExpectedAmount())
                .sum();

        Settlement settlement = Settlement.builder()
                .userId(userId)
                .status(SettlementMatchStatus.MATCHED)
                .jobId(jobId)
                .periodStart(period.start())
                .periodEnd(period.end())
                .depositDate(transaction.transactionDate())
                .expectedAmount(expectedAmount)
                .actualAmount(transaction.amount().longValueExact())
                .transactionCount(1)
                .accountTransactionId(transaction.transactionId())
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);

        Set<Long> affectedLogIds = new LinkedHashSet<>();
        for (WorkLogPlatformIncome income : newlyCoveredIncomes) {
            income.setSettlementStatus(SettlementStatus.COMPLETED);
            workLogPlatformIncomeMapper.update(income);
            affectedLogIds.add(income.getLogId());
        }
        for (Long logId : affectedLogIds) {
            recomputeWorkLogSettlementStatus(logId);
        }
        return new TransactionResult(MatchOutcome.CREATED, transactionAmount);
    }

    /** income 행 하나가 COMPLETED로 바뀌면, 그 부모 WorkLog의 상태도 다시 계산해서 갱신한다. */
    private void recomputeWorkLogSettlementStatus(Long logId) {
        WorkLog workLog = workLogMapper.findById(logId);
        if (workLog == null) {
            return;
        }
        List<WorkLogPlatformIncome> incomes = workLogPlatformIncomeMapper.findByLogId(logId);
        workLog.setSettlementStatus(WorkLogSettlementStatusCalculator.calculate(incomes));
        workLogMapper.update(workLog);
    }

    /**
     * ON_DEMAND는 실제 입금액이 특정 근무일과 대응되지 않으므로(사용자가 임의 시점에 임의
     * 금액을 출금 신청) 근무일지는 건드리지 않고, 해당 잡으로만 SETTLEMENT를 남긴다.
     * WorkLog.settlementStatus는 이미 확정(CONFIRMED) 시점에 WorkLogService가 즉시
     * COMPLETED로 처리해뒀다 (이 메서드와 독립적).
     */
    private void saveOnDemandSettlement(Long userId, Long jobId, IncomingTransaction transaction) {
        Settlement settlement = Settlement.builder()
                .userId(userId)
                .status(SettlementMatchStatus.MATCHED)
                .jobId(jobId)
                .periodStart(transaction.transactionDate())
                .periodEnd(transaction.transactionDate())
                .depositDate(transaction.transactionDate())
                .expectedAmount(0L)
                .actualAmount(transaction.amount().longValueExact())
                .transactionCount(1)
                .accountTransactionId(transaction.transactionId())
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);
    }

    /**
     * 매칭되지 않은 거래는 잡을 특정할 수 없으니 job_id=null로, 같은 날짜 것들을 합쳐
     * 하루 단위 한 행으로 저장한다. 배치가 중복 실행돼도 같은 날짜에 두 번 쌓이지 않도록
     * UNMATCHED는 (user_id, status, period) 기준으로 존재 여부를 확인한다.
     */
    private void saveUnmatchedSettlement(Long userId, LocalDate processDate, long amount, int count) {
        if (settlementMapper.existsByUserIdAndStatusAndPeriod(
                userId, SettlementMatchStatus.UNMATCHED, processDate, processDate)) {
            return;
        }

        Settlement settlement = Settlement.builder()
                .userId(userId)
                .status(SettlementMatchStatus.UNMATCHED)
                .jobId(null)
                .periodStart(processDate)
                .periodEnd(processDate)
                .depositDate(processDate)
                .expectedAmount(0L)
                .actualAmount(amount)
                .transactionCount(count)
                .accountTransactionId(null)
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);
    }

    /** 유저당 배치 실행(runDailyBatch 1회) 기준 1건만 보낸다. eventId를 (userId, today) 기준으로 잡아
     *  배치가 같은 날 재실행돼도 중복 발송되지 않게 한다. 테스트 컨트롤러(LocalSettlementBatchCommandClient)도
     *  processSettlementDetailed 성공 시 이 메서드를 호출하므로 public으로 열어둔다. */
    public void notifySettlementCompleted(Long userId, LocalDate today, int count, long totalAmount) {
        notificationPort.notify(new NotificationRequest(
                userId,
                "settlement-completed-" + userId + "-" + today,
                "WORK",
                "정산이 완료되었습니다",
                String.format("오늘 정산 %d건, 총 %,d원이 처리됐어요.", count, totalAmount)
        ));
    }

    private Long resolveJobId(Long platformId, List<Job> jobs) {
        for (Job job : jobs) {
            boolean mapped = jobPlatformMappingMapper.findByJobId(job.getJobId()).stream()
                    .anyMatch(mapping -> mapping.getPlatformId().equals(platformId));
            if (mapped) {
                return job.getJobId();
            }
        }
        return null;
    }
}
