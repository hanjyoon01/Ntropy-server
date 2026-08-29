package com.ntropy.bff.controller.diagnosis;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.diagnosis.response.DiagnosisResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.diagnosis.api.client.DiagnosisResultQueryClient;
import com.ntropy.diagnosis.api.dto.DiagnosisResultSummary;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

/**
 * 프론트엔드의 월별 재무진단 결과 조회 요청을 처리하는 BFF Controller입니다.
 *
 * 인증된 사용자 ID를 기준으로 diagnosis-service에 진단 결과 조회를
 * 요청하고 프론트 응답 형태로 변환합니다.
 */
@Api(tags = "재무진단")
@RestController
@RequestMapping("/api/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisResultQueryClient diagnosisResultQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    /**
     * 인증된 사용자의 특정 월 재무진단 결과를 조회합니다.
     *
     * 요청 예시:
     * GET /api/diagnosis/2026-07
     */
    @ApiOperation("월별 재무진단 결과 조회")
    @GetMapping("/{yearMonth}")
    public ResponseEntity<ApiResponse<DiagnosisResponse>> getDiagnosis(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable String yearMonth
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);

        DiagnosisResultSummary summary =
                diagnosisResultQueryClient.findByUserIdAndYearMonth(userId, yearMonth);

        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK.value(),
                        "재무진단 결과 조회에 성공했습니다.",
                        DiagnosisResponse.from(summary)
                )
        );
    }
}
