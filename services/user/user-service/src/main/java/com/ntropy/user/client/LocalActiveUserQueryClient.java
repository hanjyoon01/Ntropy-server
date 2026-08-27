package com.ntropy.user.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.common.domain.UserScope;
import com.ntropy.user.api.client.ActiveUserQueryClient;
import com.ntropy.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalActiveUserQueryClient
        implements ActiveUserQueryClient {

    private final UserService userService;

    @Override
    public List<Long> findActiveUserIds(UserScope scope) {
        return userService.findActiveUserIds(scope);
    }
}