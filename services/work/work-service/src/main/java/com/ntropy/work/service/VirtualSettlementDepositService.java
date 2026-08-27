package com.ntropy.work.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.work.config.SettlementBatchUserScopeProperties;
import com.ntropy.work.domain.ExpectedSettlementDateCalculator;
import com.ntropy.work.domain.SettlementPeriod;
import com.ntropy.work.domain.SettlementPeriodCalculator;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult;
import com.ntropy.work.domain.VirtualSettlementDepositBatchResult.MatchTarget;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.mapper.projection.VirtualSettlementIncome;
import com.ntropy.work.port.account.SettlementDepositOutcome;
import com.ntropy.work.port.account.SettlementDepositPort;
import com.ntropy.work.port.account.SettlementDepositRequest;
import com.ntropy.work.port.user.UserPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 확정 근무일지의 플랫폼별 예상 소득을 정산일에 NTROPY 가상계좌 입금으로 만든다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualSettlementDepositService {

    private static final String AUTO_TRIGGER = "AUTO";
    private static final String BUSINESS_DAY = "BUSINESS_DAY";
    private static final int HOLIDAY_LOOKAHEAD_DAYS = 62;

    private final UserPort userPort;
    private final SettlementBatchUserScopeProperties userScopeProperties;
    private final WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;
    private final PlatformMapper platformMapper;
    private final HolidayService holidayService;
    private final SettlementDepositPort settlementDepositPort;

    /** 전체 활성 사용자의 오늘까지 도래한 가상 정산 입금을 생성한다. */
    public VirtualSettlementDepositBatchResult runDailyBatch(LocalDate processDate) {
        List<Long> userIds = userPort.findActiveUserIds(userScopeProperties.getUserScope());
        if (userIds == null || userIds.isEmpty()) {
            return VirtualSettlementDepositBatchResult.empty();
        }

        int createdCount = 0;
        Set<MatchTarget> matchTargets = new LinkedHashSet<>();
        for (Long userId : userIds) {
            try {
                VirtualSettlementDepositBatchResult result = processUser(userId, processDate);
                createdCount += result.createdCount();
                matchTargets.addAll(result.matchTargets());
            } catch (RuntimeException e) {
                log.error("[가상 정산 입금] 사용자 처리 실패. userId={}, processDate={}", userId, processDate, e);
            }
        }
        return new VirtualSettlementDepositBatchResult(createdCount, new ArrayList<>(matchTargets));
    }

    /** 한 사용자의 미정산 플랫폼 소득을 정산일·정산기간별로 합산해 입금 명령을 전송한다. */
    public VirtualSettlementDepositBatchResult processUser(Long userId, LocalDate processDate) {
        List<VirtualSettlementIncome> incomes =
                workLogPlatformIncomeMapper.findConfirmedByUserIdUpToDateForVirtualSettlement(userId, processDate);
        if (incomes == null || incomes.isEmpty()) {
            return VirtualSettlementDepositBatchResult.empty();
        }

        Map<Long, Platform> platforms = platformMapper.findAll().stream()
                .collect(Collectors.toMap(Platform::getPlatformId, Function.identity()));
        Set<LocalDate> holidays = loadRequiredHolidays(incomes, platforms, processDate);
        Map<DepositGroupKey, DepositGroup> groups = new LinkedHashMap<>();

        for (VirtualSettlementIncome income : incomes) {
            Platform platform = platforms.get(income.getPlatformId());
            if (!isEligible(income, platform)) {
                continue;
            }
            try {
                LocalDate depositDate = ExpectedSettlementDateCalculator.calculate(
                        platform, income.getWorkDate(), holidays);
                if (depositDate.isAfter(processDate)) {
                    continue;
                }
                SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, depositDate, holidays);
                DepositGroupKey key = new DepositGroupKey(
                        platform.getPlatformId(), depositDate, period.start(), period.end());
                groups.computeIfAbsent(key, ignored -> new DepositGroup())
                        .add(income.getExpectedAmount(), income.getSettlementStatus());
            } catch (RuntimeException e) {
                // 한 플랫폼의 잘못된 정산 규칙이 같은 사용자의 다른 정상 플랫폼 입금을 막지 않게 격리한다.
                log.error("[가상 정산 입금] 플랫폼 규칙 계산 실패. userId={}, platformId={}, incomeId={}",
                        userId, income.getPlatformId(), income.getIncomeId(), e);
            }
        }

        int createdCount = 0;
        Set<MatchTarget> matchTargets = new LinkedHashSet<>();
        for (Map.Entry<DepositGroupKey, DepositGroup> entry : groups.entrySet()) {
            if (!entry.getValue().hasPending()) {
                continue;
            }
            DepositGroupKey key = entry.getKey();
            Platform platform = platforms.get(key.platformId());
            try {
                SettlementDepositOutcome result = settlementDepositPort.createOrAdjust(
                        new SettlementDepositRequest(
                                userId,
                                key.platformId(),
                                key.periodStart(),
                                key.periodEnd(),
                                key.depositDate(),
                                entry.getValue().totalAmount(),
                                platform.getDepositName()
                        ));
                if (result.available()) {
                    matchTargets.add(new MatchTarget(userId, key.depositDate()));
                }
                if (result.transactionCreated()) {
                    createdCount++;
                }
            } catch (RuntimeException e) {
                log.error("[가상 정산 입금] 정산기간 입금 생성 실패. userId={}, platformId={}, depositDate={}",
                        userId, key.platformId(), key.depositDate(), e);
            }
        }
        return new VirtualSettlementDepositBatchResult(createdCount, new ArrayList<>(matchTargets));
    }

    private Set<LocalDate> loadRequiredHolidays(
            List<VirtualSettlementIncome> incomes,
            Map<Long, Platform> platforms,
            LocalDate processDate
    ) {
        boolean needsBusinessDays = incomes.stream()
                .map(income -> platforms.get(income.getPlatformId()))
                .anyMatch(platform -> platform != null && BUSINESS_DAY.equals(platform.getSettlementOffsetUnit()));
        if (!needsBusinessDays) {
            return Set.of();
        }
        LocalDate firstWorkDate = incomes.stream()
                .map(VirtualSettlementIncome::getWorkDate)
                .min(LocalDate::compareTo)
                .orElse(processDate);
        return holidayService.getHolidays(firstWorkDate, processDate.plusDays(HOLIDAY_LOOKAHEAD_DAYS));
    }

    private static boolean isEligible(VirtualSettlementIncome income, Platform platform) {
        return platform != null
                && AUTO_TRIGGER.equals(platform.getSettlementTriggerType())
                && platform.getDepositName() != null
                && !platform.getDepositName().isBlank()
                && income.getExpectedAmount() != null
                && income.getExpectedAmount() > 0;
    }

    private record DepositGroupKey(
            Long platformId,
            LocalDate depositDate,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
    }

    private static final class DepositGroup {
        private long totalAmount;
        private boolean hasPending;

        private void add(Long amount, SettlementStatus status) {
            totalAmount = Math.addExact(totalAmount, amount);
            hasPending = hasPending || status == SettlementStatus.PENDING;
        }

        private long totalAmount() {
            return totalAmount;
        }

        private boolean hasPending() {
            return hasPending;
        }
    }
}
