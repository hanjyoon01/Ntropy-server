package com.ntropy.work.adapter.user;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.common.domain.UserScope;
import com.ntropy.user.api.client.ActiveUserQueryClient;
import com.ntropy.work.port.user.UserPort;

import lombok.RequiredArgsConstructor;

/** user-service가 발행한 ActiveUserQueryClient를 work의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class UserActiveUsersAdapter implements UserPort {

    private final ActiveUserQueryClient activeUserQueryClient;

    @Override
    public List<Long> findActiveUserIds(UserScope scope) {
        return activeUserQueryClient.findActiveUserIds(scope);
    }
}
