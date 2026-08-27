package com.ntropy.diagnosis.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ntropy.diagnosis.api.client.DiagnosisQueryClient;
import com.ntropy.diagnosis.api.dto.DiagnosisDefenseSnapshot;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.service.DiagnosisResultService;

/**
 * DiagnosisQueryClient의 diagnosis-service 내부 구현체입니다.
 *
 * defense-service는 DiagnosisQueryClient 인터페이스만 호출하고,
 * 방어모드 D-Day 계산에 필요한 재무진단 스냅샷 조합은 이 클래스가 담당합니다.
 */
@Component
public class LocalDiagnosisQueryClient implements DiagnosisQueryClient {

    private static final int RECENT_RESULT_LIMIT = 3;
    private static final int NORMALIZED_DAYS = 30;
    private static final int INTERMEDIATE_SCALE = 10;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DiagnosisResultService diagnosisResultService;
    private final Clock clock;

    /** 운영 환경에서는 서비스 표준 시간대(Asia/Seoul)의 시스템 시계를 사용합니다. */
    @Autowired
    public LocalDiagnosisQueryClient(DiagnosisResultService diagnosisResultService) {
        this(diagnosisResultService, Clock.system(SERVICE_ZONE));
    }

    /** 날짜 경계 테스트에서 고정 시계를 주입하기 위한 생성자입니다. */
    LocalDiagnosisQueryClient(DiagnosisResultService diagnosisResultService, Clock clock) {
        this.diagnosisResultService = diagnosisResultService;
        this.clock = clock;
    }

    @Override
    public DiagnosisDefenseSnapshot getDefenseSnapshot(Long userId) {
        List<DiagnosisResult> recentResults =
                diagnosisResultService.findLatestByUserId(userId, RECENT_RESULT_LIMIT);

        if (recentResults.isEmpty()) {
            return new DiagnosisDefenseSnapshot(null, null, null);
        }

        // year_month DESC로 조회되므로 첫 번째 항목이 최신 진단 결과입니다.
        DiagnosisResult latest = recentResults.get(0);

        return new DiagnosisDefenseSnapshot(
                latest.getLiquidAssets(),
                latest.getSafeAssets(),
                calculateAverageMonthlyExpense(recentResults)
        );
    }

    /**
     * 최근 최대 3개 진단결과의 totalExpense를 30일 환산해 평균을 계산합니다.
     *
     * 관측 일수를 계산할 수 없거나 0 이하인 레코드는 평균 계산에서 제외합니다
     * (최신 자산 스냅샷 매핑에는 영향을 주지 않습니다).
     */
    private Long calculateAverageMonthlyExpense(List<DiagnosisResult> recentResults) {
        BigDecimal sum = BigDecimal.ZERO;
        int usedCount = 0;

        for (DiagnosisResult result : recentResults) {
            int observedDays = observedDays(result);
            if (observedDays <= 0 || result.getTotalExpense() == null) {
                continue;
            }

            BigDecimal normalizedExpense = BigDecimal.valueOf(result.getTotalExpense())
                    .multiply(BigDecimal.valueOf(NORMALIZED_DAYS))
                    .divide(BigDecimal.valueOf(observedDays), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);

            sum = sum.add(normalizedExpense);
            usedCount++;
        }

        if (usedCount == 0) {
            return null;
        }

        return sum.divide(BigDecimal.valueOf(usedCount), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 진단 결과의 관측 일수를 결정합니다.
     *
     * 현재 월은 1일부터 calculatedAt까지의 일수, 완료된 과거 월은
     * 해당 월의 실제 전체 일수를 사용합니다. calculatedAt이 없으면
     * (정상적으로는 발생하지 않지만 방어적으로) 0을 반환해 호출부에서
     * 평균 계산 대상에서 제외되도록 합니다.
     */
    private int observedDays(DiagnosisResult result) {
        if (result.getCalculatedAt() == null) {
            return 0;
        }

        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(result.getYearMonth());
        } catch (DateTimeParseException | NullPointerException exception) {
            return 0;
        }

        YearMonth currentYearMonth = YearMonth.now(clock);

        // 미래 연월은 완료 월도 현재 월도 아니므로 평균 계산에 사용할 수 없습니다.
        if (yearMonth.isAfter(currentYearMonth)) {
            return 0;
        }

        if (yearMonth.equals(currentYearMonth)) {
            if (!YearMonth.from(result.getCalculatedAt()).equals(yearMonth)) {
                return 0;
            }
            return result.getCalculatedAt().getDayOfMonth();
        }

        return yearMonth.lengthOfMonth();
    }
}
