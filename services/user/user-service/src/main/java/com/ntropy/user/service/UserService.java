package com.ntropy.user.service;

import com.ntropy.user.client.oauth.GoogleOAuthClient;
import com.ntropy.user.client.oauth.KakaoOAuthClient;

import com.ntropy.user.config.VirtualTestProperties;
import com.ntropy.user.dto.OAuthLoginResponse;
import com.ntropy.user.security.JwtProvider;
import com.ntropy.common.domain.UserScope;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.exception.UserErrorCode;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.domain.entity.User;
import com.ntropy.user.virtual.VirtualUserProviderId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;
    private final JwtProvider jwtProvider;
    private final VirtualUserSeedService virtualUserSeedService;
    private final VirtualTestProperties virtualTestProperties;

    @Transactional
    public OAuthLoginResponse processOAuthLoginWithCode(String provider, String code) {
        log.info("소셜 로그인 처리 시작: provider={}", provider);
        User oauthUserParam = getOAuthUser(provider, code);
        return processOAuthLogin(oauthUserParam);
    }

    private User getOAuthUser(String provider, String code) {
        if ("kakao".equalsIgnoreCase(provider)) {
            String accessToken = kakaoOAuthClient.getAccessToken(code);
            return kakaoOAuthClient.getKakaoUser(accessToken);
        } else if ("google".equalsIgnoreCase(provider)) {
            String accessToken = googleOAuthClient.getAccessToken(code);
            return googleOAuthClient.getGoogleUser(accessToken);
        } else {
            throw new ServiceException(UserErrorCode.UNSUPPORTED_OAUTH_PROVIDER, provider);
        }
    }

    @Transactional
    public OAuthLoginResponse processOAuthLogin(User oauthUserParam) {
        User user = userMapper.findByProviderAndProviderId(
                oauthUserParam.getProvider(),
                oauthUserParam.getProviderId()
        ).orElseGet(() -> registerNewUser(oauthUserParam));

        return issueTokensForExistingUser(user);
    }

    /** (provider, provider_id) unique key 경합에서 진 경우에도 새 행을 만들지 않고 실제 저장된 행으로 수렴한다. */
    private User registerNewUser(User oauthUserParam) {
        User user = oauthUserParam;
        log.info("신규 회원가입 처리: provider={}, email={}", user.getProvider(), user.getEmail());
        user.setStatus("ACTIVE");
        user.setRole("ROLE_USER");
        user.setTermsAgreed(true);
        user.setOnboardingCompleted(false);

        userMapper.insertUser(user); // (provider, provider_id) 경합 시 user_id만 기존 행으로 채워짐 (ON DUPLICATE KEY UPDATE)

        // user_id 외 나머지 컬럼은 경합에서 이긴 요청이 실제로 저장한 값일 수 있으므로 항상 재조회해 신뢰한다.
        return userMapper.findByProviderAndProviderId(user.getProvider(), user.getProviderId())
                .orElseThrow(() -> new ServiceException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 이미 존재가 확인된 회원에게만 토큰을 발급하고 로그인 정보를 갱신한다. 회원을 새로 만들지 않으므로
     * 실제 OAuth 로그인(신규 회원가입 후)과 테스트 로그인(시딩된 가상회원)이 함께 재사용할 수 있다.
     */
    private OAuthLoginResponse issueTokensForExistingUser(User user) {
        log.info("로그인 처리: userId={}", user.getUserId());

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ServiceException(UserErrorCode.INACTIVE_USER, user.getStatus());
        }

        String accessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

        user.setRefreshTokenHash(refreshToken);
        user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
        userMapper.updateLoginInfo(user);

        return OAuthLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .onboardingCompleted(user.getOnboardingCompleted())
                .build();
    }

    /**
     * 시딩된 가상회원(virtualUserNumber 순번)으로 로그인한다. 이미 시딩된 회원만 조회하며,
     * 없다고 해서 새 가상회원을 만들지 않는다.
     */
    @Transactional
    public OAuthLoginResponse loginAsVirtualUser(int virtualUserNumber) {
        if (!virtualTestProperties.isEnabled()) {
            throw new ServiceException(UserErrorCode.VIRTUAL_TEST_DISABLED);
        }
        if (!virtualTestProperties.isVirtualUserNumberInRange(virtualUserNumber)) {
            throw new ServiceException(UserErrorCode.INVALID_REQUEST,
                    "virtualUserNumber는 1.." + virtualTestProperties.getUserCount() + " 범위여야 합니다: " + virtualUserNumber);
        }

        User user = virtualUserSeedService.findSeededUserByOrdinal(virtualUserNumber)
                .orElseThrow(() -> new ServiceException(UserErrorCode.USER_NOT_FOUND,
                        "시딩되지 않은 가상회원 순번입니다: " + virtualUserNumber));

        return issueTokensForExistingUser(user);
    }

    public User getUserById(Long userId) {
        return userMapper.findById(userId);
    }

    @Transactional
    public void updateUser(User user) {
        userMapper.updateUser(user);
    }

    @Transactional
    public void completeOnboarding(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_FOUND);
        }
        userMapper.updateOnboardingCompleted(userId);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userMapper.deleteUser(userId);
    }

    @Transactional
    public void logout(Long userId) {
        userMapper.invalidateRefreshToken(userId);
        log.info("사용자 로그아웃: userId={}의 Refresh Token을 폐기했습니다.", userId);
    }

    @Transactional
    public TokenRefreshResponseDto refreshAccessToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new ServiceException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userMapper.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ServiceException(UserErrorCode.INVALID_REFRESH_TOKEN));

        String newAccessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

        user.setRefreshTokenHash(newRefreshToken);
        user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
        userMapper.updateLoginInfo(user);

        log.info("Access Token 재발급 및 Refresh Token 회전 완료: userId={}", user.getUserId());

        return new TokenRefreshResponseDto(newAccessToken, newRefreshToken);
    }

    /**
     * 배치 대상 활성 사용자 ID를 scope(REAL_ONLY/VIRTUAL_ONLY/ALL) 기준으로 조회합니다.
     */
    public List<Long> findActiveUserIds(UserScope scope) {
        Objects.requireNonNull(scope, "scope가 필요합니다");
        return userMapper.findActiveUserIds(scope.name(), VirtualUserProviderId.PROVIDER);
    }
}
