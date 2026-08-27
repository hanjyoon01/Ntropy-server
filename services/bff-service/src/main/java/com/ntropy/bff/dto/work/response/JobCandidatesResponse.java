package com.ntropy.bff.dto.work.response;

import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.work.api.dto.summary.JobCandidateSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobCandidatesResponse {

    private List<JobCandidateResponse> candidates;

    public static JobCandidatesResponse from(List<JobCandidateSummary> summaries) {
        List<JobCandidateResponse> candidates = summaries.stream()
                .map(JobCandidateResponse::from)
                .collect(Collectors.toList());
        return new JobCandidatesResponse(candidates);
    }
}
