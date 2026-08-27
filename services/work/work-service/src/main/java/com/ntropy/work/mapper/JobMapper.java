package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.Job;

@Mapper
public interface JobMapper {

    void insert(Job job);

    Job findById(Long jobId);

    List<Job> findByUserId(Long userId);

    /** AI 리포트 배치용 벌크 조회: 여러 사용자의 JOB을 한 번에 조회한다. 결과는 userId로 그룹핑해서 써야 한다. */
    List<Job> findByUserIdIn(@Param("userIds") List<Long> userIds);

    void update(Job job);

    void deleteById(Long jobId);
}
