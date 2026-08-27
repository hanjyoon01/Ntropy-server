package com.ntropy.notification.adapter.user;

import org.springframework.stereotype.Component;

import com.ntropy.notification.port.user.NotificationRecipient;
import com.ntropy.notification.port.user.UserPort;
import com.ntropy.user.api.client.UserQueryClient;
import com.ntropy.user.api.dto.UserSummary;

import lombok.RequiredArgsConstructor;

/** user-service가 발행한 UserQueryClient를 notification의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class UserRecipientAdapter implements UserPort {

    private final UserQueryClient userQueryClient;

    @Override
    public NotificationRecipient findRecipient(Long userId) {
        UserSummary user = userQueryClient.getUserSummary(userId);
        if (user == null) {
            return null;
        }
        return new NotificationRecipient(user.userId(), user.alarmAgree());
    }
}
