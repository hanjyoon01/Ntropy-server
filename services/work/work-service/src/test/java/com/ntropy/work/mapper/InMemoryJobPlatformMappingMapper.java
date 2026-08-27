package com.ntropy.work.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.JobPlatformMapping;

/**
 * 테스트용 인메모리 JobPlatformMappingMapper 구현체.
 */
public class InMemoryJobPlatformMappingMapper implements JobPlatformMappingMapper {

    private final Map<Long, JobPlatformMapping> store = new LinkedHashMap<>();
    private long sequence = 1;

    @Override
    public void insert(JobPlatformMapping jobPlatformMapping) {
        jobPlatformMapping.setMappingId(sequence++);
        store.put(jobPlatformMapping.getMappingId(), jobPlatformMapping);
    }

    @Override
    public JobPlatformMapping findById(Long mappingId) {
        return store.get(mappingId);
    }

    @Override
    public List<JobPlatformMapping> findByJobId(Long jobId) {
        List<JobPlatformMapping> result = new ArrayList<>();
        for (JobPlatformMapping mapping : store.values()) {
            if (jobId.equals(mapping.getJobId())) {
                result.add(mapping);
            }
        }
        return result;
    }

    @Override
    public void update(JobPlatformMapping jobPlatformMapping) {
        store.put(jobPlatformMapping.getMappingId(), jobPlatformMapping);
    }

    @Override
    public void deleteById(Long mappingId) {
        store.remove(mappingId);
    }

    @Override
    public void deleteByJobId(Long jobId) {
        store.values().removeIf(mapping -> jobId.equals(mapping.getJobId()));
    }
}
