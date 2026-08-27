package com.ntropy.work.client;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.WorkLogCommandClient;
import com.ntropy.work.api.dto.command.WorkLogPatchCommand;
import com.ntropy.work.api.dto.command.WorkLogRegisterCommand;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.service.WorkLogService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalWorkLogCommandClient implements WorkLogCommandClient {

    private final WorkLogService workLogService;

    @Override
    public Long registerPlan(WorkLogRegisterCommand command) {
        WorkLog workLog = toWorkLog(command);
        workLogService.registerPlan(workLog);
        return workLog.getLogId();
    }

    @Override
    public Long registerActual(WorkLogRegisterCommand command) {
        WorkLog workLog = toWorkLog(command);
        workLogService.registerActual(workLog);
        return workLog.getLogId();
    }

    @Override
    public void editWorkLog(Long userId, Long logId, WorkLogPatchCommand command) {
        workLogService.editWorkLog(userId, logId, toPatch(command));
    }

    @Override
    public void confirmWorkLog(Long userId, Long logId, WorkLogPatchCommand command) {
        workLogService.confirmWorkLog(userId, logId, toPatch(command));
    }

    @Override
    public void deleteWorkLog(Long userId, Long logId) {
        workLogService.deleteWorkLog(userId, logId);
    }

    private WorkLog toWorkLog(WorkLogRegisterCommand command) {
        return WorkLog.builder()
                .userId(command.getUserId())
                .jobId(command.getJobId())
                .workDate(command.getWorkDate())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .taskCount(command.getTaskCount())
                .fatigue(command.getFatigue())
                .build();
    }

    private WorkLog toPatch(WorkLogPatchCommand command) {
        return WorkLog.builder()
                .jobId(command.getJobId())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .taskCount(command.getTaskCount())
                .fatigue(command.getFatigue())
                .build();
    }
}
