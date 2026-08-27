package com.ntropy.work.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import com.ntropy.work.domain.entity.Platform;

/**
 * PLATFORM.settlement_cycle 기준으로, 입금(정산)일로부터 실제 근무가 이뤄진 기간을 역산한다.
 * settlement_offset_day는 "정산 기간 종료일과 입금일 사이의 간격(일)"으로 DAILY/WEEKLY 공통으로
 * 쓴다 (쿠팡이츠 배달파트너 실제 사례: 매주 금요일 입금, 정산 기간은 전주 수요일~이번주 화요일
 * → 종료일(화)이 입금일(금)보다 3일 전이므로 offset=3).
 * WEEKLY에서 offset이 없으면 1(입금일 하루 전)을 기본값으로 쓰는데, 이는 검증되지 않은
 * 추정값이라 실제 플랫폼별 값이 확인되면 seed 데이터를 갱신해야 한다.
 * MONTHLY 기준일의 정확한 정산 규칙도 마찬가지로 검증되지 않은 추정값이다:
 * - MONTHLY: 입금일이 속한 달의 직전 달 전체
 *
 * <p>settlement_offset_unit이 BUSINESS_DAY인 경우 offset일만큼 역산할 때 주말·공휴일을
 * 건너뛴다. 다만 정산 요일이 고정된 WEEKLY 플랫폼은 실제 입금이 밀려도 정산기간 자체는
 * 유지해야 하므로 고정 요일과 기간 종료일의 달력상 간격을 사용한다. 주말 판정은 외부 데이터
 * 없이 계산하고, 공휴일은 holidays 파라미터로 주입받는다 - 이 클래스는 순수 함수로 유지하고,
 * 실제 공휴일 조회(홀리데이 API/캐시)는 호출부(SettlementService)의 책임으로 둔다.</p>
 *
 * <p>DAILY + BUSINESS_DAY 조합에서는 역산된 workDate 하루가 아니라, workDate부터 그 다음
 * 영업일 직전까지를 기간으로 잡는다. 주말/공휴일은 오프셋 카운트에 안 들어가므로, 그 구간
 * 안 어느 날에 일했든(예: 금·토·일 연속 근무, 또는 평일이지만 공휴일인 날) 순방향으로는
 * 전부 같은 입금일로 수렴하기 때문이다 - workDate 하루만 조회하면 같은 날 입금될 다른 날의
 * 근무일지를 놓친다.</p>
 *
 * <p>WEEKLY 정산은 PLATFORM.settlement_day_of_week(요일 고정, 예: 쿠팡이츠=FRI)가 있으면
 * 실제 입금일이 아니라 그 요일 기준 "원래 예정된 정산일"로 오프셋을 계산한다. 실제로 쿠팡이츠는
 * 정산 기간(화)과 정산일(금) 사이에 공휴일이 끼면 그 주 금요일 정산을 건너뛰고 다음 영업일(보통
 * 다음 주 월요일)로 미룬다 - 실제 입금일 그대로 오프셋을 적용하면 밀린 만큼 정산 기간 전체가
 * 같이 밀려버리므로, 항상 예정 요일부터 역산해야 한다.</p>
 */
public final class SettlementPeriodCalculator {

    private static final int DEFAULT_WEEKLY_OFFSET_DAY = 1;
    private static final String BUSINESS_DAY = "BUSINESS_DAY";

    private SettlementPeriodCalculator() {
    }

    public static SettlementPeriod calculate(Platform platform, LocalDate paymentDate, Set<LocalDate> holidays) {
        return switch (platform.getSettlementCycle()) {
            case "DAILY" -> dailyPeriod(platform, paymentDate, holidays);
            case "WEEKLY" -> weeklyPeriod(platform, paymentDate, holidays);
            case "MONTHLY" -> monthlyPeriod(paymentDate);
            default -> throw new IllegalArgumentException(
                    "알 수 없는 정산 주기입니다: " + platform.getSettlementCycle());
        };
    }

    private static SettlementPeriod dailyPeriod(Platform platform, LocalDate paymentDate, Set<LocalDate> holidays) {
        int offset = platform.getSettlementOffsetDay() == null ? 0 : platform.getSettlementOffsetDay();
        LocalDate workDate = subtractDays(paymentDate, offset, platform.getSettlementOffsetUnit(), holidays);
        LocalDate periodEnd = extendThroughTrailingNonBusinessDays(workDate, platform.getSettlementOffsetUnit(), holidays);
        return new SettlementPeriod(workDate, periodEnd);
    }

    private static SettlementPeriod weeklyPeriod(Platform platform, LocalDate paymentDate, Set<LocalDate> holidays) {
        LocalDate scheduledPaymentDate = resolveScheduledPaymentDate(platform.getSettlementDayOfWeek(), paymentDate);
        int offset = platform.getSettlementOffsetDay() == null
                ? DEFAULT_WEEKLY_OFFSET_DAY
                : platform.getSettlementOffsetDay();
        // 정산 요일이 고정된 플랫폼은 공휴일 때문에 실제 입금만 밀릴 뿐 정산기간 자체는
        // 흔들리지 않는다(쿠팡이츠: 항상 수~화 근무분). 따라서 고정 요일과 기간 종료일의
        // 간격은 달력일로 유지한다. 요일이 없는 추정 규칙만 기존 단위 기반 역산을 사용한다.
        LocalDate periodEndAnchor = platform.getSettlementDayOfWeek() == null
                ? subtractDays(scheduledPaymentDate, offset, platform.getSettlementOffsetUnit(), holidays)
                : scheduledPaymentDate.minusDays(offset);
        // periodEnd 경계일 자체가 공휴일이면 subtractDays가 그 전 영업일로 밀어버려서, 정작
        // 그 경계일(공휴일)에 일한 근무일지가 7일 범위 밖으로 빠진다 - DAILY와 같은 이유로
        // 같은 헬퍼로 경계일을 다시 확장해준다.
        LocalDate periodEnd = platform.getSettlementDayOfWeek() == null
                ? extendThroughTrailingNonBusinessDays(
                        periodEndAnchor, platform.getSettlementOffsetUnit(), holidays)
                : periodEndAnchor;
        LocalDate periodStart = periodEnd.minusDays(6);
        return new SettlementPeriod(periodStart, periodEnd);
    }

    /**
     * settlement_day_of_week가 설정돼 있으면, 실제 입금일이 그 요일이 아닐 때(정산 요일과
     * 다음 정산 요일 사이에 낀 공휴일 때문에 다음 영업일로 밀린 경우 등) 입금일 이전의
     * 가장 최근 그 요일을 "원래 예정된 정산일"로 되돌린다. 오프셋은 항상 이 예정일 기준으로
     * 계산해야 실제 입금이 밀려도 정산 기간(예: 쿠팡이츠의 전주 수~금주 화) 자체는 흔들리지
     * 않는다 - 실제 입금일 그대로 오프셋을 적용하면 밀린 만큼 기간 전체가 같이 밀려버린다.
     * 값이 없으면(플랫폼별로 아직 확인 안 된 경우) 기존처럼 실제 입금일을 그대로 쓴다.
     */
    private static LocalDate resolveScheduledPaymentDate(String settlementDayOfWeek, LocalDate actualPaymentDate) {
        if (settlementDayOfWeek == null) {
            return actualPaymentDate;
        }
        DayOfWeek scheduled = SettlementDayOfWeekParser.parse(settlementDayOfWeek);
        LocalDate date = actualPaymentDate;
        while (date.getDayOfWeek() != scheduled) {
            date = date.minusDays(1);
        }
        return date;
    }

    private static SettlementPeriod monthlyPeriod(LocalDate paymentDate) {
        YearMonth previousMonth = YearMonth.from(paymentDate).minusMonths(1);
        return new SettlementPeriod(previousMonth.atDay(1), previousMonth.atEndOfMonth());
    }

    /**
     * unit이 BUSINESS_DAY면 주말·공휴일을 건너뛰며 days만큼 역산하고, 그 외(CALENDAR_DAY 또는
     * 아직 값이 없는 경우)는 기존처럼 달력일 그대로 뺀다.
     */
    private static LocalDate subtractDays(LocalDate date, int days, String unit, Set<LocalDate> holidays) {
        if (!BUSINESS_DAY.equals(unit)) {
            return date.minusDays(days);
        }
        LocalDate result = date;
        int remaining = days;
        while (remaining > 0) {
            result = result.minusDays(1);
            if (!isWeekend(result) && !holidays.contains(result)) {
                remaining--;
            }
        }
        return result;
    }

    /**
     * BUSINESS_DAY 오프셋에서는 순방향으로 보면 "start부터 그 다음 영업일 직전까지"의
     * 비영업일(주말/공휴일) 근무도 전부 같은 입금일로 수렴한다 - 영업일이 아닌 날은
     * 오프셋 카운트에 안 들어가서, 그 구간 어느 날에 일했든 "그 다음 offset번째 영업일"은
     * 똑같이 계산되기 때문이다(예: 금·토·일 연속 근무는 셋 다 같은 날 입금됨).
     * start만 단일 조회 대상으로 삼으면 이 구간의 다른 날 근무일지를 놓치므로, start부터
     * 다음 영업일 바로 전날까지로 기간을 넓힌다. CALENDAR_DAY는 애초에 하루 단위로 1:1
     * 대응되므로 그대로 start를 반환한다.
     */
    private static LocalDate extendThroughTrailingNonBusinessDays(LocalDate start, String unit, Set<LocalDate> holidays) {
        if (!BUSINESS_DAY.equals(unit)) {
            return start;
        }
        LocalDate end = start;
        while (isWeekend(end.plusDays(1)) || holidays.contains(end.plusDays(1))) {
            end = end.plusDays(1);
        }
        return end;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}
