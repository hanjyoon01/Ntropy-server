package com.ntropy.bff.controller.work;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.work.request.SavingGoalCreateRequest;
import com.ntropy.bff.dto.work.request.SavingGoalUpdateRequest;
import com.ntropy.bff.dto.work.response.SavingGoalCreateResponse;
import com.ntropy.bff.dto.work.response.SavingGoalResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.work.api.client.SavingGoalCommandClient;
import com.ntropy.work.api.client.SavingGoalQueryClient;
import com.ntropy.user.api.client.UserCommandClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "저축목표")
@RestController
@RequestMapping("/api/saving-goals")
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingGoalCommandClient savingGoalCommandClient;
    private final SavingGoalQueryClient savingGoalQueryClient;
    private final UserCommandClient userCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("이번 달 저축목표 조회")
    @GetMapping
    public ApiResponse<SavingGoalResponse> getCurrentMonthGoal(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SavingGoalResponse response = SavingGoalResponse.from(savingGoalQueryClient.findCurrentMonthGoal(userId));
        return ApiResponse.success(response);
    }

    @ApiOperation("저축목표 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<SavingGoalCreateResponse>> createSavingGoal(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody SavingGoalCreateRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        Long savingGoalId = savingGoalCommandClient.registerSavingGoal(request.toCommand(userId));
        userCommandClient.completeOnboarding(userId);
        ApiResponse<SavingGoalCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "저축목표가 등록되었습니다.", new SavingGoalCreateResponse(savingGoalId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @ApiOperation("이번 달 저축목표 수정")
    @PutMapping
    public ApiResponse<Void> updateCurrentMonthGoal(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody SavingGoalUpdateRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        savingGoalCommandClient.updateSavingGoal(userId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "저축목표가 수정되었습니다.", null);
    }
}
