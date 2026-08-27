package com.ntropy.bff.controller.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.user.request.TokenRefreshRequest;
import com.ntropy.bff.dto.user.request.VirtualLoginRequest;
import com.ntropy.bff.dto.user.response.OAuthLoginResponse;
import com.ntropy.bff.dto.user.response.TokenRefreshResponse;
import com.ntropy.bff.dto.user.response.UserResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.user.api.client.UserCommandClient;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.api.client.UserQueryClient;
import com.ntropy.user.api.dto.UserSummary;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "인증")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserCommandClient userCommandClient;
    private final UserQueryClient userQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("소셜 로그인")
    @GetMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> oauthLogin(
            @PathVariable String provider,
            @RequestParam("code") String code
    ) {
        return ApiResponse.success(
                OAuthLoginResponse.from(userCommandClient.loginWithOAuthCode(provider, code))
        );
    }

    @ApiOperation("가상회원 테스트 로그인 (내부 검증 전용)")
    @PostMapping("/virtual-login")
    public ApiResponse<OAuthLoginResponse> virtualLogin(@RequestBody VirtualLoginRequest request) {
        if (request.getVirtualUserNumber() == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST);
        }
        return ApiResponse.success(
                OAuthLoginResponse.from(userCommandClient.loginAsVirtualUser(request.getVirtualUserNumber()))
        );
    }

    @ApiOperation("내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        UserSummary summary = userQueryClient.getUserSummary(userId);
        if (summary == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }
        return ApiResponse.success(UserResponse.from(summary));
    }

    @ApiOperation("로그아웃")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        userCommandClient.logout(userId);
        return ApiResponse.success(200, "로그아웃되었습니다.", null);
    }

    @ApiOperation("Access Token 재발급")
    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refresh(@RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(
                TokenRefreshResponse.from(userCommandClient.refreshAccessToken(request.getRefreshToken()))
        );
    }
}
