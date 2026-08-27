package com.ntropy.work.api.client;

import java.util.List;

import com.ntropy.work.api.dto.summary.JobCandidateSummary;

/**
 * work-service의 온보딩 잡 등록 후보 조회 계약. work-service가
 * LocalJobCandidateQueryClient로 구현하고, 다른 서비스(bff-service 등)는
 * 이 인터페이스만 의존한다 (모듈 격리 규칙).
 */
public interface JobCandidateQueryClient {

    List<JobCandidateSummary> getJobCandidates(Long userId);
}
