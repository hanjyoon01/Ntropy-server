package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.client.holiday.HolidayApiClient;
import com.ntropy.work.client.holiday.HolidayApiItem;
import com.ntropy.work.mapper.InMemoryHolidayMapper;

class HolidayServiceTest {

    private InMemoryHolidayMapper holidayMapper;
    private StubHolidayApiClient apiClient;
    private HolidayService service;

    @BeforeEach
    void setUp() {
        holidayMapper = new InMemoryHolidayMapper();
        apiClient = new StubHolidayApiClient();
        service = new HolidayService(apiClient, holidayMapper);
    }

    @Test
    @DisplayName("처음 조회하는 연도는 API를 호출해 결과를 반환한다")
    void getHolidays_uncachedYear_callsApiAndReturnsHolidays() {
        apiClient.itemsByYear.put(2026, List.of(
                item(20260101, "1월1일", "Y"),
                item(20260301, "삼일절", "Y")
        ));

        Set<LocalDate> holidays = service.getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)), holidays);
        assertEquals(1, apiClient.callCountByYear.getOrDefault(2026, 0));
    }

    @Test
    @DisplayName("isHoliday=N인 항목은 저장하지 않는다")
    void getHolidays_nonHolidayItem_isExcluded() {
        apiClient.itemsByYear.put(2026, List.of(
                item(20260101, "1월1일", "Y"),
                item(20260405, "식목일", "N")
        ));

        Set<LocalDate> holidays = service.getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(Set.of(LocalDate.of(2026, 1, 1)), holidays);
    }

    @Test
    @DisplayName("이미 캐싱된 연도는 API를 다시 호출하지 않는다")
    void getHolidays_alreadyCachedYear_doesNotCallApiAgain() {
        apiClient.itemsByYear.put(2026, List.of(item(20260101, "1월1일", "Y")));
        service.getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        service.getHolidays(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertEquals(1, apiClient.callCountByYear.getOrDefault(2026, 0));
    }

    @Test
    @DisplayName("연도 경계에 걸친 범위는 두 연도 모두 캐시를 보장한다")
    void getHolidays_rangeCrossingYearBoundary_cachesBothYears() {
        apiClient.itemsByYear.put(2025, List.of(item(20251225, "크리스마스", "Y")));
        apiClient.itemsByYear.put(2026, List.of(item(20260101, "1월1일", "Y")));

        Set<LocalDate> holidays = service.getHolidays(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 5));

        assertEquals(Set.of(LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 1)), holidays);
        assertTrue(apiClient.callCountByYear.containsKey(2025));
        assertTrue(apiClient.callCountByYear.containsKey(2026));
    }

    @Test
    @DisplayName("조회 범위 밖의 공휴일은 결과에 포함되지 않는다")
    void getHolidays_outOfRangeHoliday_isExcluded() {
        apiClient.itemsByYear.put(2026, List.of(
                item(20260101, "1월1일", "Y"),
                item(20261225, "크리스마스", "Y")
        ));

        Set<LocalDate> holidays = service.getHolidays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(Set.of(LocalDate.of(2026, 1, 1)), holidays);
    }

    private static HolidayApiItem item(long locdate, String dateName, String isHoliday) {
        HolidayApiItem item = new HolidayApiItem();
        item.setLocdate(locdate);
        item.setDateName(dateName);
        item.setIsHoliday(isHoliday);
        return item;
    }

    private static final class StubHolidayApiClient extends HolidayApiClient {

        private final java.util.Map<Integer, List<HolidayApiItem>> itemsByYear = new java.util.HashMap<>();
        private final java.util.Map<Integer, Integer> callCountByYear = new java.util.HashMap<>();

        StubHolidayApiClient() {
            super(null, null);
        }

        @Override
        public List<HolidayApiItem> fetchHolidays(int year) {
            callCountByYear.merge(year, 1, Integer::sum);
            return itemsByYear.getOrDefault(year, List.of());
        }
    }
}
