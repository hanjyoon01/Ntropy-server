package com.ntropy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import com.ntropy.defense.adapter.diagnosis.DiagnosisSnapshotAdapter;
import com.ntropy.defense.domain.DefenseCalculationStatus;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.mapper.DefenseModeMapper;
import com.ntropy.defense.port.diagnosis.DiagnosisRecalculationPort;
import com.ntropy.defense.service.DefenseModeService;
import com.ntropy.diagnosis.client.LocalDiagnosisQueryClient;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;
import com.ntropy.diagnosis.service.DiagnosisResultService;

/** api 조립 환경에서 defense-service가 diagnosis-service의 실제 Client를 주입받는지 검증합니다. */
class DiagnosisDefenseWiringTest {

    @Configuration
    @ComponentScan(
            basePackages = "com.ntropy",
            useDefaultFilters = false,
            includeFilters = @Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            LocalDiagnosisQueryClient.class,
                            DiagnosisSnapshotAdapter.class,
                            DiagnosisResultService.class,
                            DefenseModeService.class
                    }
            )
    )
    static class TestConfig {

        @Bean
        DiagnosisRecalculationPort diagnosisRecalculationPort() {
            // 이 와이어링 테스트는 기존 재무진단 결과 조회 경로만 검증하므로 재계산은 no-op으로 둔다.
            return (userId, yearMonth) -> { };
        }

        @Bean
        DiagnosisResultMapper diagnosisResultMapper() {
            YearMonth completedMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
            DiagnosisResult result = new DiagnosisResult(
                    1L,
                    7L,
                    completedMonth.toString(),
                    0L,
                    0L,
                    completedMonth.lengthOfMonth() * 10_000L,
                    0L,
                    0L,
                    null,
                    5_000_000L,
                    3_000_000L,
                    2_000_000L,
                    LocalDateTime.of(completedMonth.getYear(), completedMonth.getMonth(), 1, 9, 0),
                    null,
                    null,
                    null
            );

            return new DiagnosisResultMapper() {
                @Override
                public int upsert(DiagnosisResult diagnosisResult) {
                    return 0;
                }

                @Override
                public DiagnosisResult findByUserIdAndYearMonth(Long userId, String yearMonth) {
                    return null;
                }

                @Override
                public List<DiagnosisResult> findLatestByUserId(Long userId, int limit) {
                    return userId.equals(result.getUserId()) ? List.of(result) : List.of();
                }
            };
        }

        @Bean
        DefenseModeMapper defenseModeMapper() {
            return new InMemoryDefenseModeMapper();
        }
    }

    @Test
    void defenseModeUsesLocalDiagnosisQueryClientInsteadOfFallback() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DefenseModeService defenseModeService = context.getBean(DefenseModeService.class);

            DefenseMode entered = defenseModeService.enter(new DefenseModeEnterCommand(
                    7L,
                    "OTHER",
                    LocalDate.of(2026, 8, 3),
                    LocalDate.of(2026, 8, 10)
            ));

            assertEquals(3_000_000L, entered.getReserveAmountSnapshot());
            assertEquals(2_000_000L, entered.getSafeAssetAmountSnapshot());
            assertEquals(300_000L, entered.getAverageMonthlyExpense());
            assertEquals(DefenseCalculationStatus.CALCULATED, entered.getCalculationStatus());
        }
    }

    private static class InMemoryDefenseModeMapper implements DefenseModeMapper {

        private final Map<Long, DefenseMode> data = new HashMap<>();
        private long sequence;

        @Override
        public DefenseMode findById(Long defenseId) {
            return data.get(defenseId);
        }

        @Override
        public DefenseMode findActiveByUserId(Long userId) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()))
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);
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
        public java.util.List<DefenseMode> findScheduledToActivate(java.time.LocalDate today) {
            return data.values().stream()
                    .filter(mode -> mode.getStatus() == DefenseModeStatus.SCHEDULED)
                    .filter(mode -> !mode.getUnavailableStartDate().isAfter(today))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<DefenseMode> findCalendarPeriods(Long userId, LocalDate from, LocalDate to) {
            return new ArrayList<>();
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
}
