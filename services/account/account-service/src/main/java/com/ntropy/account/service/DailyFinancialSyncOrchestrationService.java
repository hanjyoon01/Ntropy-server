package com.ntropy.account.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ntropy.account.api.client.DailyFinancialSyncClient;
import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.config.FinancialSyncBatchUserScopeProperties;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 활성 사용자의 일일 금융거래 증분 동기화를 provider별로 실행한다.
 *
 * <p>NTROPY는 주 데이터 경로이므로 CODEF보다 먼저 호출한다. 각 provider는 서로 다른
 * {@code DAILY_BATCH_EXECUTION.job_name}과 lease를 사용하며, 한 provider의 예외가 다른
 * provider 실행을 막지 않도록 호출 경계를 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyFinancialSyncOrchestrationService {

    private static final List<DailyFinancialSyncProvider> PROVIDER_ORDER = List.of(
            DailyFinancialSyncProvider.NTROPY,
            DailyFinancialSyncProvider.CODEF
    );

    // account-service 단독 검증 컨텍스트도 기동될 수 있도록 Optional로 주입하되,
    // 실제 스케줄 실행 시 구현체가 없으면 조용히 건너뛰지 않고 명시적으로 실패한다.
    private final Optional<UserPort> userPort;
    private final FinancialSyncBatchUserScopeProperties userScopeProperties;
    private final DailyFinancialSyncClient dailyFinancialSyncClient;
    private final Clock clock;

    /** 서울 시간 기준으로 완전히 종료된 전날 거래를 동기화한다. */
    public void runPreviousDayBatch() {
        runBatch(LocalDate.now(clock).minusDays(1));
    }

    /** 지정한 영업일에 대해 활성 사용자의 provider별 동기화를 실행한다. */
    public void runBatch(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate가 필요합니다");

        UserPort port = userPort.orElseThrow(
                () -> new IllegalStateException("UserPort 구현체가 필요합니다")
        );
        List<Long> activeUserIds = normalizeActiveUserIds(
                port.findActiveUserIds(userScopeProperties.getUserScope())
        );
        if (activeUserIds.isEmpty()) {
            log.info("[일일 금융거래 동기화] 대상 사용자가 없습니다. businessDate={}", businessDate);
            return;
        }

        log.info(
                "[일일 금융거래 동기화] 실행 시작. businessDate={}, activeUserCount={}",
                businessDate, activeUserIds.size()
        );

        for (DailyFinancialSyncProvider provider : PROVIDER_ORDER) {
            synchronizeProvider(provider, activeUserIds, businessDate);
        }

        log.info(
                "[일일 금융거래 동기화] provider 호출 완료. businessDate={}, activeUserCount={}",
                businessDate, activeUserIds.size()
        );
    }

    private void synchronizeProvider(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                     LocalDate businessDate) {
        try {
            DailyFinancialSyncResult result = dailyFinancialSyncClient.synchronize(
                    provider, activeUserIds, businessDate
            );
            if (result == null) {
                log.error(
                        "[일일 금융거래 동기화] provider가 결과를 반환하지 않았습니다. "
                                + "provider={}, businessDate={}",
                        provider, businessDate
                );
                return;
            }

            log.info(
                    "[일일 금융거래 동기화] provider 완료. provider={}, businessDate={}, "
                            + "status={}, successUserCount={}, partialFailedUserCount={}, transactionCount={}",
                    provider,
                    businessDate,
                    result.executionStatus(),
                    result.successfulUserIds().size(),
                    result.partialFailedUserIds().size(),
                    result.processedTransactionCount()
            );
        } catch (RuntimeException providerFailure) {
            log.error(
                    "[일일 금융거래 동기화] provider 호출 실패. provider={}, businessDate={}",
                    provider, businessDate, providerFailure
            );
        }
    }

    private static List<Long> normalizeActiveUserIds(List<Long> activeUserIds) {
        if (activeUserIds == null || activeUserIds.isEmpty()) {
            return List.of();
        }
        return activeUserIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
