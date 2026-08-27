package com.ntropy.bff.dto.work.response;

import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.work.api.dto.summary.JobSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobsResponse {

    private List<JobResponse> jobs;

    public static JobsResponse from(List<JobSummary> summaries) {
        List<JobResponse> jobs = summaries.stream()
                .map(JobResponse::from)
                .collect(Collectors.toList());
        return new JobsResponse(jobs);
    }
}
