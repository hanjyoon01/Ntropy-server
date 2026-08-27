package com.ntropy.user.client;

import com.ntropy.user.api.client.UserQueryClient;
import com.ntropy.user.api.dto.UserSummary;
import com.ntropy.user.domain.entity.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** user-service가 구현하는 회원 조회 계약. */
@Component
@RequiredArgsConstructor
public class LocalUserQueryClient implements UserQueryClient {

    private final UserService userService;

    @Override
    public UserSummary getUserSummary(Long userId) {
        User user = userService.getUserById(userId);
        if (user == null) {
            return null;
        }
        return new UserSummary(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getProvider(),
                user.getAlarmAgree(),
                user.getLocationAgree(),
                user.getOnboardingCompleted()
        );
    }
}
