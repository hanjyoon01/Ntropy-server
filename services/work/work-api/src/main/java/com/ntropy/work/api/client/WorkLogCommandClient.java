package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.command.WorkLogPatchCommand;
import com.ntropy.work.api.dto.command.WorkLogRegisterCommand;

/**
 * work-service의 WORK_LOG 쓰기 계약. work-service가 LocalWorkLogCommandClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface WorkLogCommandClient {

    Long registerPlan(WorkLogRegisterCommand command);

    Long registerActual(WorkLogRegisterCommand command);

    /** userId는 요청자 본인 확인용 - logId가 그 사람 소유가 아니면 예외. */
    void editWorkLog(Long userId, Long logId, WorkLogPatchCommand command);

    /** userId는 요청자 본인 확인용 - logId가 그 사람 소유가 아니면 예외. */
    void confirmWorkLog(Long userId, Long logId, WorkLogPatchCommand command);

    /** userId는 요청자 본인 확인용 - logId가 그 사람 소유가 아니면 예외. */
    void deleteWorkLog(Long userId, Long logId);
}
