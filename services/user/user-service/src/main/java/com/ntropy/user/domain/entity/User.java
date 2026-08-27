package com.ntropy.user.domain.entity;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private Long userId;                  // user_id
    private String email;                 // email
    private String name;                  // name
    private String providerId;            // provider_id
    @JsonIgnore
    private String refreshTokenHash;      // refresh_token_hash
    private Boolean alarmAgree;           // alarm_agree
    private Boolean locationAgree;        // location_agree
    @JsonIgnore
    private LocalDateTime refreshTokenExpireAt; // refresh_token_expire_at
    private String provider;              // provider (KAKAO, GOOGLE 등)
    private String status;                // status
    private String role;                  // role
    private LocalDateTime createdAt;      // created_at
    private LocalDateTime lastLoginAt;    // last_login_at
    private Boolean onboardingCompleted;  // onboarding_completed
    private Boolean termsAgreed;          // terms_agreed

}
