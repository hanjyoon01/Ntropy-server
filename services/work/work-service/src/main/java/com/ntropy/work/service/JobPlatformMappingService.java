package com.ntropy.work.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.JobPlatformMapping;
import com.ntropy.work.exception.WorkErrorCode;
import com.ntropy.work.mapper.JobPlatformMappingMapper;
import com.ntropy.work.mapper.PlatformMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobPlatformMappingService {

    private final JobPlatformMappingMapper jobPlatformMappingMapper;
    private final PlatformMapper platformMapper;

    public List<JobPlatformMapping> findByJobId(Long jobId) {
        return jobPlatformMappingMapper.findByJobId(jobId);
    }

    public JobPlatformMapping register(Long jobId, Long platformId) {
        if (platformMapper.findById(platformId) == null) {
            throw new ServiceException(WorkErrorCode.PLATFORM_NOT_FOUND, "platformId=" + platformId);
        }
        boolean alreadyMapped = jobPlatformMappingMapper.findByJobId(jobId).stream()
                .anyMatch(mapping -> mapping.getPlatformId().equals(platformId));
        if (alreadyMapped) {
            throw new ServiceException(WorkErrorCode.JOB_PLATFORM_MAPPING_ALREADY_EXISTS,
                    "jobId=" + jobId + ", platformId=" + platformId);
        }

        JobPlatformMapping mapping = JobPlatformMapping.builder()
                .jobId(jobId)
                .platformId(platformId)
                .build();
        jobPlatformMappingMapper.insert(mapping);
        return mapping;
    }

    public void deleteById(Long mappingId) {
        jobPlatformMappingMapper.deleteById(mappingId);
    }

    /**
     * 잡의 플랫폼 매핑을 전체 교체한다(잡 수정 PUT과 동일하게 부분 수정이 아니라 통째로 갈아끼움).
     * platformIds가 null/빈 리스트면 기존 매핑을 전부 지우고 끝난다.
     */
    @Transactional
    public void replaceForJob(Long jobId, List<Long> platformIds) {
        jobPlatformMappingMapper.deleteByJobId(jobId);
        if (platformIds == null) {
            return;
        }
        for (Long platformId : platformIds.stream().distinct().toList()) {
            if (platformMapper.findById(platformId) == null) {
                throw new ServiceException(WorkErrorCode.PLATFORM_NOT_FOUND, "platformId=" + platformId);
            }
            JobPlatformMapping mapping = JobPlatformMapping.builder()
                    .jobId(jobId)
                    .platformId(platformId)
                    .build();
            jobPlatformMappingMapper.insert(mapping);
        }
    }
}
