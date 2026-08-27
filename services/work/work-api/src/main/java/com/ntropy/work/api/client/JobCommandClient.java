package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.command.JobRegisterCommand;
import com.ntropy.work.api.dto.command.JobUpdateCommand;

/**
 * work-service의 JOB 쓰기 계약. work-service가 LocalJobCommandClient로 구현하고,
 * bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface JobCommandClient {

    Long registerJob(JobRegisterCommand command);

    /** userId는 요청자 본인 확인용 - jobId가 그 사람 소유가 아니면 예외. */
    void updateJob(Long userId, Long jobId, JobUpdateCommand command);

    /** userId는 요청자 본인 확인용 - jobId가 그 사람 소유가 아니면 예외. */
    void deactivateJob(Long userId, Long jobId);
}
