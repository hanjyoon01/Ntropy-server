package com.ntropy.notification.service;

import java.util.HashMap;
import java.util.Map;

import com.ntropy.notification.port.user.NotificationRecipient;
import com.ntropy.notification.port.user.UserPort;

/** 알림 수신 동의(alarm_agree) 값을 회원별로 지정할 수 있는 테스트용 스텁. */
class StubUserQueryClient implements UserPort {

    private final Map<Long, Boolean> alarmAgreeByUserId = new HashMap<>();

    StubUserQueryClient withAlarmAgree(Long userId, boolean alarmAgree) {
        alarmAgreeByUserId.put(userId, alarmAgree);
        return this;
    }

    @Override
    public NotificationRecipient findRecipient(Long userId) {
        if (!alarmAgreeByUserId.containsKey(userId)) {
            return null;
        }
        return new NotificationRecipient(userId, alarmAgreeByUserId.get(userId));
    }
}
