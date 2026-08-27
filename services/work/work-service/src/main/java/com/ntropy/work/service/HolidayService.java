package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.work.client.holiday.HolidayApiClient;
import com.ntropy.work.client.holiday.HolidayApiItem;
import com.ntropy.work.domain.entity.Holiday;
import com.ntropy.work.mapper.HolidayMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공휴일 조회 + 캐싱. 연도 단위로 HOLIDAY 테이블에 캐시해두고, 아직 캐싱 안 된 연도만
 * 특일 정보 API(HolidayApiClient)를 호출한다. isHoliday="N"인 항목(관공서 임시공휴일이
 * 아닌 일반 기념일 등 참고용 데이터)은 저장하지 않는다.
 *
 * <p>연도 캐싱 여부는 HOLIDAY 테이블에 해당 연도 행이 1건이라도 있는지로 판단한다.
 * 한국은 매년 최소 신정 등 공휴일이 있으므로, 0건이면 아직 API로 채운 적 없다고 본다.</p>
 */
@Service
@RequiredArgsConstructor
public class HolidayService {

    private static final DateTimeFormatter LOCDATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String IS_HOLIDAY_YES = "Y";

    private final HolidayApiClient holidayApiClient;
    private final HolidayMapper holidayMapper;

    /** startDate~endDate에 걸친 연도를 전부 캐시 보장한 뒤, 그 범위의 공휴일 날짜 Set을 반환한다. */
    public Set<LocalDate> getHolidays(LocalDate startDate, LocalDate endDate) {
        for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
            ensureYearCached(year);
        }
        List<Holiday> holidays = holidayMapper.findByDateRange(startDate, endDate);
        return holidays.stream().map(Holiday::getHolidayDate).collect(Collectors.toSet());
    }

    private void ensureYearCached(int year) {
        if (holidayMapper.countByYear(year) > 0) {
            return;
        }
        List<HolidayApiItem> items = holidayApiClient.fetchHolidays(year);
        for (HolidayApiItem item : items) {
            if (!IS_HOLIDAY_YES.equals(item.getIsHoliday())) {
                continue;
            }
            holidayMapper.insert(Holiday.builder()
                    .holidayDate(LocalDate.parse(String.valueOf(item.getLocdate()), LOCDATE_FORMAT))
                    .name(item.getDateName())
                    .build());
        }
    }
}
