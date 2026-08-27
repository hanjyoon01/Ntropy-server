package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.client.DailyFinancialSyncClient;
import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.account.config.FinancialSyncBatchUserScopeProperties;
import com.ntropy.account.port.user.SeededVirtualUserBatch;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;
import com.ntropy.common.domain.UserScope;

class DailyFinancialSyncOrchestrationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 15);
    private static final FinancialSyncBatchUserScopeProperties REAL_ONLY_SCOPE =
            new FinancialSyncBatchUserScopeProperties("REAL_ONLY");

    private static UserPort userPortReturning(Function<UserScope, List<Long>> findActiveUserIds) {
        return new UserPort() {
            @Override
            public List<Long> findActiveUserIds(UserScope scope) {
                return findActiveUserIds.apply(scope);
            }

            @Override
            public SeededVirtualUserBatch findSeededVirtualUsers() {
                throw new UnsupportedOperationException("이 테스트에서는 사용하지 않습니다");
            }
        };
    }

    @Test
    void runsPreviousSeoulDayAndCallsNtropyBeforeCodef() {
        RecordingDailyFinancialSyncClient syncClient = new RecordingDailyFinancialSyncClient();
        DailyFinancialSyncOrchestrationService service = new DailyFinancialSyncOrchestrationService(
                Optional.of(userPortReturning(scope -> Arrays.asList(2L, null, 2L, 1L))),
                REAL_ONLY_SCOPE,
                syncClient,
                Clock.fixed(Instant.parse("2026-08-15T15:30:00Z"), SEOUL)
        );

        service.runPreviousDayBatch();

        assertEquals(
                List.of(DailyFinancialSyncProvider.NTROPY, DailyFinancialSyncProvider.CODEF),
                syncClient.providers
        );
        assertEquals(List.of(BUSINESS_DATE, BUSINESS_DATE), syncClient.businessDates);
        assertEquals(List.of(2L, 1L), syncClient.activeUserIdsByCall.get(0));
        assertEquals(List.of(2L, 1L), syncClient.activeUserIdsByCall.get(1));
    }

    @Test
    void skipsProvidersWhenThereAreNoActiveUsers() {
        RecordingDailyFinancialSyncClient syncClient = new RecordingDailyFinancialSyncClient();
        DailyFinancialSyncOrchestrationService service = new DailyFinancialSyncOrchestrationService(
                Optional.of(userPortReturning(scope -> List.of())), REAL_ONLY_SCOPE, syncClient, Clock.system(SEOUL)
        );

        service.runBatch(BUSINESS_DATE);

        assertTrue(syncClient.providers.isEmpty());
    }

    @Test
    void continuesWithCodefWhenNtropyInvocationThrows() {
        RecordingDailyFinancialSyncClient syncClient = new RecordingDailyFinancialSyncClient();
        syncClient.throwingProvider = DailyFinancialSyncProvider.NTROPY;
        DailyFinancialSyncOrchestrationService service = new DailyFinancialSyncOrchestrationService(
                Optional.of(userPortReturning(scope -> List.of(1L))), REAL_ONLY_SCOPE, syncClient, Clock.system(SEOUL)
        );

        service.runBatch(BUSINESS_DATE);

        assertEquals(
                List.of(DailyFinancialSyncProvider.NTROPY, DailyFinancialSyncProvider.CODEF),
                syncClient.providers
        );
    }

    @Test
    void treatsNullActiveUserResultAsEmpty() {
        RecordingDailyFinancialSyncClient syncClient = new RecordingDailyFinancialSyncClient();
        UserPort userPort = userPortReturning(scope -> null);
        DailyFinancialSyncOrchestrationService service = new DailyFinancialSyncOrchestrationService(
                Optional.of(userPort), REAL_ONLY_SCOPE, syncClient, Clock.system(SEOUL)
        );

        service.runBatch(BUSINESS_DATE);

        assertTrue(syncClient.providers.isEmpty());
    }

    @Test
    void passesConfiguredUserScopeToActiveUserQueryClient() {
        RecordingDailyFinancialSyncClient syncClient = new RecordingDailyFinancialSyncClient();
        List<UserScope> requestedScopes = new ArrayList<>();
        DailyFinancialSyncOrchestrationService service = new DailyFinancialSyncOrchestrationService(
                Optional.of(userPortReturning(scope -> {
                    requestedScopes.add(scope);
                    return List.of();
                })),
                REAL_ONLY_SCOPE, syncClient, Clock.system(SEOUL)
        );

        service.runBatch(BUSINESS_DATE);

        assertEquals(List.of(UserScope.REAL_ONLY), requestedScopes);
    }

    @Test
    void failsClearlyWhenActiveUserClientIsMissingAtExecutionTime() {
        DailyFinancialSyncOrchestrationService service = new DailyFinancialSyncOrchestrationService(
                Optional.empty(), REAL_ONLY_SCOPE, new RecordingDailyFinancialSyncClient(), Clock.system(SEOUL)
        );

        assertThrows(IllegalStateException.class, () -> service.runBatch(BUSINESS_DATE));
    }

    private static final class RecordingDailyFinancialSyncClient implements DailyFinancialSyncClient {

        private final List<DailyFinancialSyncProvider> providers = new ArrayList<>();
        private final List<LocalDate> businessDates = new ArrayList<>();
        private final List<List<Long>> activeUserIdsByCall = new ArrayList<>();
        private DailyFinancialSyncProvider throwingProvider;

        @Override
        public DailyFinancialSyncResult synchronize(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                                    LocalDate businessDate) {
            providers.add(provider);
            businessDates.add(businessDate);
            activeUserIdsByCall.add(List.copyOf(activeUserIds));
            if (provider == throwingProvider) {
                throw new IllegalStateException("provider failure");
            }
            return new DailyFinancialSyncResult(
                    businessDate, provider, "SUCCESS", activeUserIds, Map.of(), List.of(), List.of(), 0
            );
        }
    }
}
