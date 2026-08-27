package com.ntropy.work.client;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.JobCandidateQueryClient;
import com.ntropy.work.api.dto.summary.JobCandidateSummary;
import com.ntropy.work.api.dto.summary.PlatformMatchSummary;
import com.ntropy.work.domain.JobCandidate;
import com.ntropy.work.domain.PlatformMatch;
import com.ntropy.work.service.OnboardingJobCandidateService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalJobCandidateQueryClient implements JobCandidateQueryClient {

    private final OnboardingJobCandidateService onboardingJobCandidateService;

    @Override
    public List<JobCandidateSummary> getJobCandidates(Long userId) {
        return onboardingJobCandidateService.deriveJobCandidates(userId).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    private JobCandidateSummary toSummary(JobCandidate candidate) {
        return JobCandidateSummary.builder()
                .categoryId(candidate.categoryId())
                .categoryName(candidate.categoryName())
                .platforms(candidate.platforms().stream()
                        .map(this::toPlatformSummary)
                        .collect(Collectors.toList()))
                .settlementCount(candidate.settlementCount())
                .totalAmount(candidate.totalAmount())
                .build();
    }

    private PlatformMatchSummary toPlatformSummary(PlatformMatch match) {
        return PlatformMatchSummary.builder()
                .platformId(match.platformId())
                .platformName(match.platformName())
                .depositName(match.depositName())
                .build();
    }
}
