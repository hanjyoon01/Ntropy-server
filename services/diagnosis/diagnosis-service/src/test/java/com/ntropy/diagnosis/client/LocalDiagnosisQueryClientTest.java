package com.ntropy.diagnosis.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.diagnosis.api.dto.DiagnosisDefenseSnapshot;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.service.DiagnosisResultService;

class LocalDiagnosisQueryClientTest {

    @Test
    void returnsAllNullSnapshotWhenNoDiagnosisResultExists() {
        LocalDiagnosisQueryClient client = clientWith();

        DiagnosisDefenseSnapshot snapshot = client.getDefenseSnapshot(1L);

        assertNull(snapshot.getReserveAmount());
        assertNull(snapshot.getSafeAssetAmount());
        assertNull(snapshot.getAverageMonthlyExpense());
    }

    @Test
    void mapsLatestResultAssetsToReserveAndSafeAssetAmount() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth twoMonthsAgo = YearMonth.now().minusMonths(2);

        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(result(1L, twoMonthsAgo, 900_000L, 10_000L, 20_000L));
        mapper.save(result(1L, lastMonth, 1_000_000L, 500_000L, 300_000L));

        LocalDiagnosisQueryClient client = clientWith(mapper);

        DiagnosisDefenseSnapshot snapshot = client.getDefenseSnapshot(1L);

        // 최신(lastMonth) 결과의 liquidAssets/safeAssets가 매핑돼야 합니다.
        assertEquals(500_000L, snapshot.getReserveAmount());
        assertEquals(300_000L, snapshot.getSafeAssetAmount());
    }

    @Test
    void usesAtMostThreeMostRecentResultsForAverage() {
        InMemoryMapper threeResultsOnly = new InMemoryMapper();
        InMemoryMapper withTwoOlderOutliers = new InMemoryMapper();
        for (int i = 1; i <= 3; i++) {
            DiagnosisResult recent = result(1L, YearMonth.now().minusMonths(i), 300_000L * i, 0L, 0L);
            threeResultsOnly.save(recent);
            withTwoOlderOutliers.save(recent);
        }
        // 4번째·5번째로 오래된 결과는 극단적인 값이라, 평균에 반영되면 결과가 달라져야 합니다.
        for (int i = 4; i <= 5; i++) {
            withTwoOlderOutliers.save(result(1L, YearMonth.now().minusMonths(i), 999_999_000L, 0L, 0L));
        }

        Long averageWithThree = clientWith(threeResultsOnly).getDefenseSnapshot(1L).getAverageMonthlyExpense();
        Long averageWithFiveSaved = clientWith(withTwoOlderOutliers).getDefenseSnapshot(1L).getAverageMonthlyExpense();

        // 최근 3건만 평균에 사용되므로, 더 오래된 극단값이 있어도 결과가 동일해야 합니다.
        assertEquals(averageWithThree, averageWithFiveSaved);
    }

    @Test
    void normalizesCompletedMonthByItsActualLength() {
        YearMonth completedMonth = YearMonth.now().minusMonths(1);
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(result(1L, completedMonth, completedMonth.lengthOfMonth() * 10_000L, 0L, 0L));

        LocalDiagnosisQueryClient client = clientWith(mapper);

        DiagnosisDefenseSnapshot snapshot = client.getDefenseSnapshot(1L);

        // totalExpense = lengthOfMonth * 10,000이므로 30일 환산 시 300,000이어야 합니다.
        assertEquals(300_000L, snapshot.getAverageMonthlyExpense());
    }

    @Test
    void normalizesCurrentMonthByElapsedDaysSinceCalculatedAt() {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime calculatedAt = currentMonth.atDay(1).plusDays(4).atTime(9, 0);

        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(resultWithCalculatedAt(1L, currentMonth, 500_000L, 0L, 0L, calculatedAt));

        LocalDiagnosisQueryClient client = clientWith(mapper);

        DiagnosisDefenseSnapshot snapshot = client.getDefenseSnapshot(1L);

        // 1일부터 calculatedAt(5일)까지 관측 5일 → totalExpense * 30 / 5 = 3,000,000
        assertEquals(3_000_000L, snapshot.getAverageMonthlyExpense());
    }

    @Test
    void usesAsiaSeoulClockAtUtcMonthBoundary() {
        ZoneId serviceZone = ZoneId.of("Asia/Seoul");
        Clock monthBoundaryClock = Clock.fixed(
                Instant.parse("2026-08-31T15:30:00Z"),
                serviceZone
        );
        YearMonth currentMonthInSeoul = YearMonth.of(2026, 9);

        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(resultWithCalculatedAt(
                1L,
                currentMonthInSeoul,
                100_000L,
                0L,
                0L,
                LocalDateTime.of(2026, 9, 1, 0, 20)
        ));

        LocalDiagnosisQueryClient client = new LocalDiagnosisQueryClient(
                new DiagnosisResultService(mapper),
                monthBoundaryClock
        );

        assertEquals(
                3_000_000L,
                client.getDefenseSnapshot(1L).getAverageMonthlyExpense()
        );
    }

    @Test
    void excludesMalformedYearMonthFromExpenseAverageWithoutFailingSnapshot() {
        DiagnosisResult malformed = new DiagnosisResult(
                null, 1L, "2026-13",
                0L, 0L, 500_000L, 0L, 0L, null,
                800_000L, 500_000L, 300_000L,
                LocalDateTime.of(2026, 12, 31, 9, 0), null, null, null
        );
        InMemoryMapper mapper = new InMemoryMapper();
        mapper.save(malformed);

        DiagnosisDefenseSnapshot snapshot = clientWith(mapper).getDefenseSnapshot(1L);

        assertEquals(500_000L, snapshot.getReserveAmount());
        assertEquals(300_000L, snapshot.getSafeAssetAmount());
        assertNull(snapshot.getAverageMonthlyExpense());
    }

    private LocalDiagnosisQueryClient clientWith(InMemoryMapper mapper) {
        return new LocalDiagnosisQueryClient(new DiagnosisResultService(mapper));
    }

    private LocalDiagnosisQueryClient clientWith() {
        return clientWith(new InMemoryMapper());
    }

    private DiagnosisResult result(
            Long userId, YearMonth yearMonth, Long totalExpense, Long liquidAssets, Long safeAssets
    ) {
        return resultWithCalculatedAt(
                userId, yearMonth, totalExpense, liquidAssets, safeAssets, LocalDateTime.now()
        );
    }

    private DiagnosisResult resultWithCalculatedAt(
            Long userId, YearMonth yearMonth, Long totalExpense, Long liquidAssets, Long safeAssets,
            LocalDateTime calculatedAt
    ) {
        return new DiagnosisResult(
                null,
                userId,
                yearMonth.toString(),
                0L,
                0L,
                totalExpense,
                0L,
                0L,
                null,
                liquidAssets + safeAssets,
                liquidAssets,
                safeAssets,
                calculatedAt,
                null,
                null,
                null
        );
    }

    /** 실제 DB 대신 Map을 사용하는 테스트용 Mapper 구현체입니다. */
    private static class InMemoryMapper implements DiagnosisResultMapper {

        private final Map<String, DiagnosisResult> storage = new HashMap<>();

        void save(DiagnosisResult result) {
            storage.put(result.getUserId() + "-" + result.getYearMonth(), result);
        }

        @Override
        public int upsert(DiagnosisResult diagnosisResult) {
            save(diagnosisResult);
            return 1;
        }

        @Override
        public DiagnosisResult findByUserIdAndYearMonth(Long userId, String yearMonth) {
            return storage.get(userId + "-" + yearMonth);
        }

        @Override
        public List<DiagnosisResult> findLatestByUserId(Long userId, int limit) {
            return storage.values().stream()
                    .filter(result -> result.getUserId().equals(userId))
                    .sorted(Comparator.comparing(DiagnosisResult::getYearMonth).reversed())
                    .limit(limit)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }
}
