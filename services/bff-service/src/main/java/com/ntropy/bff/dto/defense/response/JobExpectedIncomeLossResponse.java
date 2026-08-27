package com.ntropy.bff.dto.defense.response;

import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobExpectedIncomeLossResponse {
    private Long jobId;
    private String jobName;
    private Long expectedIncomeLoss;

    public static JobExpectedIncomeLossResponse from(JobExpectedIncomeLossSummary summary) {
        return new JobExpectedIncomeLossResponse(
                summary.getJobId(), summary.getJobName(), summary.getExpectedIncomeLoss());
    }
}
