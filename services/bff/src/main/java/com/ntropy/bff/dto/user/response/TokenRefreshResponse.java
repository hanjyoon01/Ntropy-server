package com.ntropy.bff.dto.user.response;

import com.ntropy.user.api.dto.TokenPair;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken
) {

    public static TokenRefreshResponse from(TokenPair tokenPair) {
        return new TokenRefreshResponse(tokenPair.accessToken(), tokenPair.refreshToken());
    }

    // Jackson 2.9는 Java record 접근자를 Bean getter로 인식하지 못하므로 HTTP 응답 직렬화용 getter를 제공한다.
    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
