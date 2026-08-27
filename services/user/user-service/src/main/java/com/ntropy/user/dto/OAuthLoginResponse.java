package com.ntropy.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class OAuthLoginResponse {
    private String accessToken;
    private String refreshToken;

    private Long userId;
    private String name;
    private String email;
    private Boolean onboardingCompleted;
}
