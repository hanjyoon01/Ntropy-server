package com.ntropy.user.api.client;

import com.ntropy.user.api.dto.OAuthLoginResult;
import com.ntropy.user.api.dto.TokenPair;
import com.ntropy.user.api.dto.UserUpdateCommand;

/** 회원 도메인이 제공할 인증·회원 명령 계약. */
public interface UserCommandClient {

    OAuthLoginResult loginWithOAuthCode(String provider, String code);

    /** 시딩된 가상회원(virtualUserNumber 순번)으로 로그인해 실제 로그인과 동일한 토큰을 발급한다. */
    OAuthLoginResult loginAsVirtualUser(int virtualUserNumber);

    TokenPair refreshAccessToken(String refreshToken);

    void logout(Long userId);

    void updateUser(Long userId, UserUpdateCommand command);

    void deleteUser(Long userId);

    void completeOnboarding(Long userId);
}
