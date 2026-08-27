package com.ntropy.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ntropy.common.domain.UserScope;

import lombok.Getter;

/** 월간 AI 리포트 배치(MonthlyAiReportOrchestrationService)가 대상으로 삼을 사용자 범위. 기본값은 REAL_ONLY로 고정한다. */
@Getter
@Component
public class AiReportBatchUserScopeProperties {

    private final UserScope userScope;

    public AiReportBatchUserScopeProperties(
            @Value("${batch.ai-report.user-scope:REAL_ONLY}") String userScope
    ) {
        this.userScope = UserScope.fromConfigValue(userScope);
    }
}
