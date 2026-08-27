package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ntropy.work.domain.entity.JobPlatformMapping;

@Mapper
public interface JobPlatformMappingMapper {

    void insert(JobPlatformMapping jobPlatformMapping);

    JobPlatformMapping findById(Long mappingId);

    List<JobPlatformMapping> findByJobId(Long jobId);

    void update(JobPlatformMapping jobPlatformMapping);

    void deleteById(Long mappingId);

    void deleteByJobId(Long jobId);
}
