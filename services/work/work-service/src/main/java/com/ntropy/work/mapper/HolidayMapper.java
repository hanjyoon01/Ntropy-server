package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.Holiday;

@Mapper
public interface HolidayMapper {

    void insert(Holiday holiday);

    /** 이 연도가 이미 캐싱됐는지 판단하는 용도 - 0건이면 아직 API로 채운 적 없다고 본다. */
    int countByYear(@Param("year") int year);

    List<Holiday> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
