package com.ntropy.user.client;

import com.ntropy.user.security.JwtProvider;
import com.ntropy.user.api.client.TokenVerifier;
import com.ntropy.user.api.dto.VerifiedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** user-service가 구현하는 액세스 토큰 검증 계약. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalTokenVerifier implements TokenVerifier {

    private final JwtProvider jwtProvider;

    @Override
    public VerifiedToken verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank() || !jwtProvider.validateToken(accessToken)) {
            return null;
        }
        try {
            return new VerifiedToken(Long.valueOf(jwtProvider.getUserId(accessToken)), jwtProvider.getRole(accessToken));
        } catch (RuntimeException e) {
            log.warn("토큰에서 인증 정보를 추출하지 못했습니다: {}", e.getMessage());
            return null;
        }
    }
}
