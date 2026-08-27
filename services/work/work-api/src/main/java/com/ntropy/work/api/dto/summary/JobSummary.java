package com.ntropy.work.api.dto.summary;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * work-service의 JOB을 다른 서비스/bff-service에 노출하기 위한 공유 DTO.
 * 서버 간 계약이라 work-service 내부 사정(created_at 등 감사 컬럼)은 담지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSummary {

    private Long jobId;
    private Long userId;
    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
    private Boolean isActive;
    private List<JobScheduleSummary> schedules;
    private List<PlatformBrief> platforms;
}
