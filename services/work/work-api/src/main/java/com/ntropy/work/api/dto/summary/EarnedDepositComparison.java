package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잡별 발생소득(확정 근무일지 기준)과 실입금소득(매칭된 입금 거래 기준) 비교.
 * differenceAmount가 음수라고 해서 미지급을 의미하지는 않는다(다음 달 입금 가능성).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EarnedDepositComparison {

    private Long jobId;
    private String jobName;
    private Long earnedIncome;
    private Long depositedIncome;
    private Long differenceAmount;
}
