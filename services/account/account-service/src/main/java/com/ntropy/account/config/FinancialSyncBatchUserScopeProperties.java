package com.ntropy.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ntropy.common.domain.UserScope;

import lombok.Getter;

/** 일일 금융거래 동기화 배치(DailyFinancialSyncOrchestrationService)가 대상으로 삼을 사용자 범위. 기본값은 REAL_ONLY로 고정한다. */
@Getter
@Component
public class FinancialSyncBatchUserScopeProperties {

    private final UserScope userScope;

    public FinancialSyncBatchUserScopeProperties(
            @Value("${batch.financial-sync.user-scope:REAL_ONLY}") String userScope
    ) {
        this.userScope = UserScope.fromConfigValue(userScope);
    }
}
