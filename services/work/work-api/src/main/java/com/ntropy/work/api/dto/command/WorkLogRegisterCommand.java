package com.ntropy.work.api.dto.command;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무일지 등록 요청. 계획(/plan)과 계획 외 실제 등록(/actual)이 필드 구성이 동일해 공용으로 쓴다.
 * plan: fatigue/taskCount 비우면 work-service가 각각 job.baseFatigue/null로 채움(PLANNED)
 * actual: fatigue 필수, taskCount는 PER_TASK 잡일 때만 필수(CONFIRMED로 즉시 생성)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkLogRegisterCommand {

    private Long userId;
    private Long jobId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long taskCount;
    private Long fatigue;
}
