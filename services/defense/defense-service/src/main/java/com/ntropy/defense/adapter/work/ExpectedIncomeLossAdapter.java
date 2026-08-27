package com.ntropy.defense.adapter.work;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.ExpectedIncomeLossQueryClient;
import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;
import com.ntropy.defense.port.work.ExpectedIncomeLossPort;
import com.ntropy.defense.port.work.JobExpectedIncomeLoss;

import lombok.RequiredArgsConstructor;

/** work-service가 발행한 ExpectedIncomeLossQueryClient를 defense의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class ExpectedIncomeLossAdapter implements ExpectedIncomeLossPort {

    private final ExpectedIncomeLossQueryClient expectedIncomeLossQueryClient;

    @Override
    public List<JobExpectedIncomeLoss> findExpectedIncomeLossByJob(Long userId, LocalDate fromDate, LocalDate toDate) {
        return expectedIncomeLossQueryClient.findExpectedIncomeLossByJob(userId, fromDate, toDate).stream()
                .map(ExpectedIncomeLossAdapter::toPort)
                .toList();
    }

    private static JobExpectedIncomeLoss toPort(JobExpectedIncomeLossSummary summary) {
        return new JobExpectedIncomeLoss(summary.getJobId(), summary.getJobName(), summary.getExpectedIncomeLoss());
    }
}
