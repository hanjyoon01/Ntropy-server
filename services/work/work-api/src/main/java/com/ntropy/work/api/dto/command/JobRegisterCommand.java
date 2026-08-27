package com.ntropy.work.api.dto.command;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잡 등록 요청. jobId/createdAt/updatedAt/isActive는 work-service가 채우므로 여기 없음.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobRegisterCommand {

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

    /** 선택. 값이 있으면 각 platformId마다 JOBPLATFORMMAPPING이 하나씩 등록됨. */
    private List<Long> platformIds;

    /** isRegular=true면 최소 1개 필요 (JobService 검증 규칙과 동일). */
    private List<JobScheduleCommand> schedules;
}
