package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
/** work-service가 방어모드에 전달하는 요청 기간 내 잡별 예상 손실소득. */
public class JobExpectedIncomeLossSummary {
    private Long jobId;
    private String jobName;
    private Long expectedIncomeLoss;
}
