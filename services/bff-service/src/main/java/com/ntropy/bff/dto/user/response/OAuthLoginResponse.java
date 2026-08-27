package com.ntropy.bff.dto.user.response;

import com.ntropy.user.api.dto.OAuthLoginResult;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String name,
        String email,
        Boolean onboardingCompleted
) {

    public static OAuthLoginResponse from(OAuthLoginResult result) {
        return new OAuthLoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.userId(),
                result.name(),
                result.email(),
                result.onboardingCompleted()
        );
    }

    // Jackson 2.9는 Java record 접근자를 Bean getter로 인식하지 못하므로 HTTP 응답 직렬화용 getter를 제공한다.
    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Boolean getOnboardingCompleted() {
        return onboardingCompleted;
    }
}
