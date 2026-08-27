package com.ntropy.bff.dto.work.request;

import java.time.LocalTime;

import com.ntropy.work.api.dto.command.WorkLogPatchCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무일지 수정(edit)/확정(confirm) 공용 요청 DTO. 모든 필드 선택.
 */
@Getter
@NoArgsConstructor
public class WorkLogPatchRequest {

    private Long jobId;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long taskCount;
    private Long fatigue;

    public WorkLogPatchCommand toCommand() {
        return new WorkLogPatchCommand(jobId, startTime, endTime, taskCount, fatigue);
    }
}
