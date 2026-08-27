package com.ntropy.ai.adapter.user;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.ai.port.user.AiUser;
import com.ntropy.ai.port.user.UserPort;
import com.ntropy.common.domain.UserScope;
import com.ntropy.user.api.client.ActiveUserQueryClient;
import com.ntropy.user.api.client.UserQueryClient;
import com.ntropy.user.api.dto.UserSummary;

import lombok.RequiredArgsConstructor;

/** user-service가 발행한 ActiveUserQueryClient/UserQueryClient를 ai의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class UserAdapter implements UserPort {

    private final ActiveUserQueryClient activeUserQueryClient;
    private final UserQueryClient userQueryClient;

    @Override
    public List<Long> findActiveUserIds(UserScope scope) {
        return activeUserQueryClient.findActiveUserIds(scope);
    }

    @Override
    public AiUser findUser(Long userId) {
        UserSummary user = userQueryClient.getUserSummary(userId);
        if (user == null) {
            return null;
        }
        return new AiUser(user.userId(), user.email());
    }
}
