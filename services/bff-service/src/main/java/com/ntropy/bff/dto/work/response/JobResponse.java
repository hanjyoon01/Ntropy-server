package com.ntropy.bff.dto.work.response;

import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.work.api.dto.summary.JobSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JOB은 급여/정산 정보를 담고 있어 common의 JobSummary를 그대로 노출하지 않고
 * 필드 단위로 감싼다. work-service 내부 사정으로 JobSummary가 바뀌어도
 * 프론트 응답 계약은 이 클래스가 그대로 지켜준다.
 */
@Getter
@NoArgsConstructor
public class JobResponse {

    private Long jobId;
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
    private List<JobScheduleResponse> schedules;
    private List<PlatformBriefResponse> platforms;

    public static JobResponse from(JobSummary summary) {
        JobResponse response = new JobResponse();
        response.jobId = summary.getJobId();
        response.categoryId = summary.getCategoryId();
        response.jobName = summary.getJobName();
        response.settlementType = summary.getSettlementType();
        response.hourlyWage = summary.getHourlyWage();
        response.monthlyWage = summary.getMonthlyWage();
        response.perTaskWage = summary.getPerTaskWage();
        response.taskPerHour = summary.getTaskPerHour();
        response.isRegular = summary.getIsRegular();
        response.baseFatigue = summary.getBaseFatigue();
        response.isActive = summary.getIsActive();
        response.schedules = summary.getSchedules() == null
                ? List.of()
                : summary.getSchedules().stream()
                        .map(JobScheduleResponse::from)
                        .collect(Collectors.toList());
        response.platforms = summary.getPlatforms() == null
                ? List.of()
                : summary.getPlatforms().stream()
                        .map(PlatformBriefResponse::from)
                        .collect(Collectors.toList());
        return response;
    }
}
