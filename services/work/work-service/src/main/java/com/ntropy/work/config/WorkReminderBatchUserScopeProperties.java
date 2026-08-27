package com.ntropy.work.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ntropy.common.domain.UserScope;

import lombok.Getter;

/** 근무일지 리마인더 배치(WorkLogReminderService)가 대상으로 삼을 사용자 범위. 기본값은 REAL_ONLY로 고정한다. */
@Getter
@Component
public class WorkReminderBatchUserScopeProperties {

    private final UserScope userScope;

    public WorkReminderBatchUserScopeProperties(
            @Value("${batch.work-reminder.user-scope:REAL_ONLY}") String userScope
    ) {
        this.userScope = UserScope.fromConfigValue(userScope);
    }
}
