package com.ntropy.bff.dto.user.response;

import com.ntropy.user.api.dto.UserSummary;

public record UserResponse(
        Long userId,
        String name,
        String email,
        String provider,
        Boolean alarmAgree,
        Boolean locationAgree,
        Boolean onboardingCompleted
) {

    public static UserResponse from(UserSummary summary) {
        return new UserResponse(
                summary.userId(),
                summary.name(),
                summary.email(),
                summary.provider(),
                summary.alarmAgree(),
                summary.locationAgree(),
                summary.onboardingCompleted()
        );
    }

    // Jackson 2.9는 Java record 접근자를 Bean getter로 인식하지 못하므로 HTTP 응답 직렬화용 getter를 제공한다.
    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getProvider() {
        return provider;
    }

    public Boolean getAlarmAgree() {
        return alarmAgree;
    }

    public Boolean getLocationAgree() {
        return locationAgree;
    }

    public Boolean getOnboardingCompleted() {
        return onboardingCompleted;
    }
}
