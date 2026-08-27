package com.ntropy.work.api.dto.command;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잡 수정 요청. userId(소유자 변경 불가)는 포함하지 않음.
 * schedules와 platformIds는 부분 수정이 아니라 전체 교체(넘어온 리스트로 기존 것을 다 갈아끼움).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JobUpdateCommand {

    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
    private List<JobScheduleCommand> schedules;
    private List<Long> platformIds;
}
