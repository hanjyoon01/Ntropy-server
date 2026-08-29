package com.ntropy.bff.dto.work.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.ntropy.work.api.dto.command.WorkLogRegisterCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무 계획 등록(/plan)과 계획 외 근무일지 등록(/actual)이 공용으로 쓰는 요청 DTO.
 */
@Getter
@NoArgsConstructor
public class WorkLogRegisterRequest {

    private Long jobId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long taskCount;
    private Long fatigue;

    public WorkLogRegisterCommand toCommand(Long userId) {
        return new WorkLogRegisterCommand(userId, jobId, workDate, startTime, endTime, taskCount, fatigue);
    }
}
