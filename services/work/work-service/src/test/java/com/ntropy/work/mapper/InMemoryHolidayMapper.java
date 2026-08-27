package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.Holiday;

/**
 * 테스트용 인메모리 HolidayMapper 구현체.
 */
public class InMemoryHolidayMapper implements HolidayMapper {

    private final Map<LocalDate, Holiday> store = new LinkedHashMap<>();

    public void seed(Holiday holiday) {
        store.put(holiday.getHolidayDate(), holiday);
    }

    @Override
    public void insert(Holiday holiday) {
        store.putIfAbsent(holiday.getHolidayDate(), holiday);
    }

    @Override
    public int countByYear(int year) {
        return (int) store.keySet().stream().filter(date -> date.getYear() == year).count();
    }

    @Override
    public List<Holiday> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Holiday> result = new ArrayList<>();
        for (Holiday holiday : store.values()) {
            LocalDate date = holiday.getHolidayDate();
            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                result.add(holiday);
            }
        }
        return result;
    }
}
