package com.ntropy.work.api.dto.command;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 근무일지 수정(edit)/확정(confirm) 공용 요청. 모든 필드가 선택이며,
 * 값이 있는 필드만 work-service에서 덮어쓴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkLogPatchCommand {

    private Long jobId;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long taskCount;
    private Long fatigue;
}
