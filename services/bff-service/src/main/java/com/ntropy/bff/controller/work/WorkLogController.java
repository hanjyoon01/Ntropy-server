package com.ntropy.bff.controller.work;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.work.request.WorkLogPatchRequest;
import com.ntropy.bff.dto.work.request.WorkLogRegisterRequest;
import com.ntropy.bff.dto.work.response.WorkLogCreateResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.work.api.client.WorkLogCommandClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "근무일지")
@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkLogController {

    private final WorkLogCommandClient workLogCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("근무 계획 등록")
    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<WorkLogCreateResponse>> registerPlan(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody WorkLogRegisterRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        Long workId = workLogCommandClient.registerPlan(request.toCommand(userId));
        ApiResponse<WorkLogCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "근무 계획이 등록되었습니다.", new WorkLogCreateResponse(workId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @ApiOperation("근무일지 등록")
    @PostMapping("/actual")
    public ResponseEntity<ApiResponse<WorkLogCreateResponse>> registerActual(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody WorkLogRegisterRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        Long workId = workLogCommandClient.registerActual(request.toCommand(userId));
        ApiResponse<WorkLogCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "근무일지가 등록되었습니다.", new WorkLogCreateResponse(workId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @ApiOperation("근무일지 수정")
    @PatchMapping("/{workId}/edit")
    public ApiResponse<Void> editWorkLog(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long workId, @RequestBody WorkLogPatchRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        workLogCommandClient.editWorkLog(userId, workId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "근무일지가 수정되었습니다.", null);
    }

    @ApiOperation("근무일지 확정")
    @PatchMapping("/{workId}/confirm")
    public ApiResponse<Void> confirmWorkLog(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long workId, @RequestBody WorkLogPatchRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        workLogCommandClient.confirmWorkLog(userId, workId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "근무일지가 확정되었습니다.", null);
    }

    @ApiOperation("근무일지 삭제")
    @DeleteMapping("/{workId}")
    public ApiResponse<Void> deleteWorkLog(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long workId) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        workLogCommandClient.deleteWorkLog(userId, workId);
        return ApiResponse.success(HttpStatus.OK.value(), "근무일지가 삭제되었습니다.", null);
    }
}
