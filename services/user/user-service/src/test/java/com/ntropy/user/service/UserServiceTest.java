package com.ntropy.user.service;

import com.ntropy.user.client.oauth.GoogleOAuthClient;
import com.ntropy.user.client.oauth.KakaoOAuthClient;

import com.ntropy.user.config.VirtualTestProperties;
import com.ntropy.user.dto.OAuthLoginResponse;
import com.ntropy.user.security.JwtProvider;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserMapper userMapper;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private VirtualUserSeedService virtualUserSeedService;
    @Mock
    private VirtualTestProperties virtualTestProperties;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("test@example.com")
                .name("Test User")
                .provider("KAKAO")
                .providerId("kakao123")
                .status("ACTIVE")
                .role("ROLE_USER")
                .onboardingCompleted(false)
                .termsAgreed(true)
                .build();
    }

    @Test
    @DisplayName("신규 회원가입 및 로그인 성공")
    void processOAuthLogin_new_user_success() {
        // insertUser 이후에는 항상 재조회하므로, 최초 조회는 없음(empty)을, insertUser 이후 재조회는 저장된 행을 반환한다.
        when(userMapper.findByProviderAndProviderId(anyString(), anyString()))
                .thenReturn(Optional.empty(), Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(1L);
            return null;
        }).when(userMapper).insertUser(any(User.class));

        OAuthLoginResponse response = userService.processOAuthLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getUserId()).isEqualTo(1L);
        verify(userMapper, times(1)).insertUser(any(User.class));
        verify(userMapper, times(2)).findByProviderAndProviderId(anyString(), anyString());
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
        verify(jwtProvider).createAccessToken("1", testUser.getEmail(), testUser.getRole());
    }

    @Test
    @DisplayName("기존 회원 로그인 성공")
    void processOAuthLogin_existing_user_success() {
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        OAuthLoginResponse response = userService.processOAuthLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
    }

    @Test
    @DisplayName("비활성 계정 로그인 시도 시 실패")
    void processOAuthLogin_inactive_user_failure() {
        testUser.setStatus("INACTIVE");
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.processOAuthLogin(testUser));
        assertThat(exception.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Access Token 재발급 성공")
    void refreshAccessToken_success() {
        String oldRefreshToken = "validOldRefreshToken";
        when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);
        when(userMapper.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        TokenRefreshResponseDto response = userService.refreshAccessToken(oldRefreshToken);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
    }

    @Test
    @DisplayName("로그아웃으로 무효화된 Refresh Token으로는 재발급할 수 없다")
    void refreshAccessToken_afterLogout_failure() {
        String oldRefreshToken = "revokedRefreshToken";
        when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);

        userService.logout(testUser.getUserId());
        // logout은 refresh_token_hash를 NULL로 만들므로, 이후 같은 토큰으로는 회원을 찾을 수 없다.
        when(userMapper.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.refreshAccessToken(oldRefreshToken));
        assertThat(exception.getStatusCode()).isEqualTo(401);
        verify(userMapper, times(1)).invalidateRefreshToken(testUser.getUserId());
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 시도 시 실패")
    void refreshAccessToken_invalid_token_failure() {
        String invalidRefreshToken = "invalidToken";
        when(jwtProvider.validateToken(invalidRefreshToken)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.refreshAccessToken(invalidRefreshToken));
        assertThat(exception.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("지원하지 않는 소셜 제공자로 로그인 시도 시 실패")
    void processOAuthLoginWithCode_unsupported_provider_failure() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.processOAuthLoginWithCode("naver", "code"));
        assertThat(exception.getStatusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() {
        userService.logout(testUser.getUserId());

        verify(userMapper, times(1)).invalidateRefreshToken(testUser.getUserId());
    }

    @Test
    @DisplayName("온보딩 완료 처리 성공")
    void completeOnboarding_success() {
        when(userMapper.findById(testUser.getUserId())).thenReturn(testUser);

        userService.completeOnboarding(testUser.getUserId());

        verify(userMapper, times(1)).updateOnboardingCompleted(testUser.getUserId());
    }

    @Test
    @DisplayName("존재하지 않는 유저의 온보딩 완료 처리 시도 시 실패")
    void completeOnboarding_userNotFound_throws() {
        when(userMapper.findById(testUser.getUserId())).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.completeOnboarding(testUser.getUserId()));
        assertThat(exception.getStatusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("가상회원 테스트 로그인 성공")
    void loginAsVirtualUser_success() {
        when(virtualTestProperties.isEnabled()).thenReturn(true);
        when(virtualTestProperties.isVirtualUserNumberInRange(1)).thenReturn(true);
        when(virtualUserSeedService.findSeededUserByOrdinal(1)).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("virtualAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("virtualRefreshToken");

        OAuthLoginResponse response = userService.loginAsVirtualUser(1);

        assertThat(response.getAccessToken()).isEqualTo("virtualAccessToken");
        assertThat(response.getUserId()).isEqualTo(testUser.getUserId());
        verify(userMapper, never()).insertUser(any(User.class));
    }

    @Test
    @DisplayName("virtual-test.enabled=false면 테스트 로그인 실패")
    void loginAsVirtualUser_disabled_failure() {
        when(virtualTestProperties.isEnabled()).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.loginAsVirtualUser(1));
        assertThat(exception.getStatusCode()).isEqualTo(404);
        verifyNoInteractions(virtualUserSeedService);
    }

    @Test
    @DisplayName("virtualUserNumber가 범위를 벗어나면 테스트 로그인 실패")
    void loginAsVirtualUser_outOfRange_failure() {
        when(virtualTestProperties.isEnabled()).thenReturn(true);
        when(virtualTestProperties.isVirtualUserNumberInRange(51)).thenReturn(false);
        when(virtualTestProperties.getUserCount()).thenReturn(50);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.loginAsVirtualUser(51));
        assertThat(exception.getStatusCode()).isEqualTo(400);
        verifyNoInteractions(virtualUserSeedService);
    }

    @Test
    @DisplayName("시딩되지 않은 순번이면 새 가상회원을 만들지 않고 실패")
    void loginAsVirtualUser_notSeeded_failure() {
        when(virtualTestProperties.isEnabled()).thenReturn(true);
        when(virtualTestProperties.isVirtualUserNumberInRange(7)).thenReturn(true);
        when(virtualUserSeedService.findSeededUserByOrdinal(7)).thenReturn(Optional.empty());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.loginAsVirtualUser(7));
        assertThat(exception.getStatusCode()).isEqualTo(404);
        verify(userMapper, never()).insertUser(any(User.class));
    }
}
