package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.WorkLog;

@Mapper
public interface WorkLogMapper {

    void insert(WorkLog workLog);

    WorkLog findById(Long logId);

    List<WorkLog> findByJobId(Long jobId);

    List<WorkLog> findByUserIdAndWorkDate(@Param("userId") Long userId, @Param("workDate") LocalDate workDate);

    List<WorkLog> findByUserIdAndDateRange(@Param("userId") Long userId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    /** AI 리포트 배치용 벌크 조회: 여러 사용자의 WORK_LOG를 한 번에 조회한다. 결과는 userId로 그룹핑해서 써야 한다. */
    List<WorkLog> findByUserIdInAndDateRange(@Param("userIds") List<Long> userIds,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    void update(WorkLog workLog);

    void deleteById(Long logId);
}
