package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월별 소득분석에서 잡별 소득 비중을 나타내는 DTO. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobIncomeSummary {

    private Long jobId;
    private String jobName;
    private Long incomeAmount;
    private Double incomeRatio;
    private Integer transactionCount;
}
