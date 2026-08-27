package com.ntropy.defense.mapper;

import com.ntropy.defense.domain.DefenseMode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DefenseModeMapper {
    DefenseMode findById(Long defenseId);
    DefenseMode findActiveByUserId(Long userId);
    DefenseMode findCurrentByUserId(Long userId);
    List<DefenseMode> findScheduledToActivate(@Param("today") LocalDate today);
    List<DefenseMode> findCalendarPeriods(@Param("userId") Long userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
    int insert(DefenseMode defenseMode);
    int activate(DefenseMode defenseMode);
    int release(DefenseMode defenseMode);
}
