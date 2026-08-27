package com.ntropy.bff.controller.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.dto.user.request.UserUpdateRequest;
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

@Api(tags = "회원")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryClient userQueryClient;
    private final UserCommandClient userCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(UserResponse.from(requireUser(userId)));
    }

    @ApiOperation("내 정보 수정")
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMyInfo(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody UserUpdateRequest request
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        if (request == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST);
        }
        userCommandClient.updateUser(userId, request.toCommand());
        return ApiResponse.success(UserResponse.from(requireUser(userId)));
    }

    @ApiOperation("회원 탈퇴")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMyAccount(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        userCommandClient.deleteUser(userId);
        return ApiResponse.success(200, "회원 탈퇴가 완료되었습니다.", null);
    }

    private UserSummary requireUser(Long userId) {
        UserSummary summary = userQueryClient.getUserSummary(userId);
        if (summary == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }
        return summary;
    }
}
