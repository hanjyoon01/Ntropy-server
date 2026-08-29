package com.ntropy.bff.controller.work;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.request.JobCreateRequest;
import com.ntropy.bff.dto.work.response.JobCandidatesResponse;
import com.ntropy.bff.dto.work.response.JobCreateResponse;
import com.ntropy.bff.dto.work.response.JobResponse;
import com.ntropy.bff.dto.work.request.JobUpdateRequest;
import com.ntropy.bff.dto.work.response.JobsResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.work.api.client.JobCandidateQueryClient;
import com.ntropy.work.api.client.JobCommandClient;
import com.ntropy.work.api.client.JobQueryClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "잡")
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobQueryClient jobQueryClient;
    private final JobCommandClient jobCommandClient;
    private final JobCandidateQueryClient jobCandidateQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("잡 단건 조회")
    @GetMapping("/{jobId}")
    public ApiResponse<JobResponse> getJob(@PathVariable Long jobId) {
        return ApiResponse.success(JobResponse.from(jobQueryClient.getJob(jobId)));
    }

    @ApiOperation("내 잡 목록 조회")
    @GetMapping
    public ApiResponse<JobsResponse> getJobs(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(JobsResponse.from(jobQueryClient.getJobsByUserId(userId)));
    }

    @ApiOperation("잡 등록 후보 조회")
    @GetMapping("/candidates")
    public ApiResponse<JobCandidatesResponse> getJobCandidates(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(JobCandidatesResponse.from(jobCandidateQueryClient.getJobCandidates(userId)));
    }

    @ApiOperation("잡 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<JobCreateResponse>> createJob(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody JobCreateRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        Long jobId = jobCommandClient.registerJob(request.toCommand(userId));
        ApiResponse<JobCreateResponse> body =
                ApiResponse.success(HttpStatus.CREATED.value(), "잡이 등록되었습니다.", new JobCreateResponse(jobId));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @ApiOperation("잡 수정")
    @PutMapping("/{jobId}")
    public ApiResponse<Void> updateJob(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long jobId, @RequestBody JobUpdateRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        jobCommandClient.updateJob(userId, jobId, request.toCommand());
        return ApiResponse.success(HttpStatus.OK.value(), "잡이 수정되었습니다.", null);
    }

    @ApiOperation("잡 비활성화")
    @PatchMapping("/{jobId}/deactivate")
    public ApiResponse<Void> deactivateJob(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long jobId) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        jobCommandClient.deactivateJob(userId, jobId);
        return ApiResponse.success(HttpStatus.OK.value(), "잡이 비활성화되었습니다.", null);
    }
}
