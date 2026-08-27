package com.ntropy.work.api.client;

import java.util.List;

import com.ntropy.work.api.dto.summary.JobSummary;

/**
 * work-service의 JOB 조회 계약. work-service가 LocalJobQueryClient로 구현하고,
 * 다른 서비스(bff-service 등)는 이 인터페이스만 의존한다 (모듈 격리 규칙).
 */
public interface JobQueryClient {

    JobSummary getJob(Long jobId);

    List<JobSummary> getJobsByUserId(Long userId);
}
