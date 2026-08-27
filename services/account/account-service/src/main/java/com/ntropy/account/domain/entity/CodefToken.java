package com.ntropy.account.domain.entity;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF OAuth2 accessToken 캐시 레코드.
 * client_credentials 방식이라 사용자 단위가 아닌 클라이언트(서비스) 단위로 하나만 유효하다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CodefToken {

    private Long id;
    private String serviceType;
    private String clientId;
    private String accessToken;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }

    public boolean isUsableAt(LocalDateTime now, Duration refreshSkew) {
        return expiresAt != null && now.plus(refreshSkew).isBefore(expiresAt);
    }
}
