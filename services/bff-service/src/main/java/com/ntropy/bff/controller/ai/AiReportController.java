package com.ntropy.bff.controller.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.ntropy.bff.dto.ai.AiReportResponse;
import com.ntropy.bff.dto.ai.AiReportEmailDeliveryResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.ai.api.client.AiReportEmailDeliveryClient;
import com.ntropy.ai.api.client.AiReportQueryClient;
import com.ntropy.ai.api.dto.AiReportSummary;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import java.util.List;

import com.ntropy.bff.dto.ai.AiReportListResponse;

/**
 * 프론트엔드의 AI 월간 리포트 조회 요청을 처리하는 BFF Controller입니다.
 *
 * 인증된 사용자 ID를 기준으로
 * ai-service에 AI 리포트 조회를 요청하고 프론트 응답 형태로 변환합니다.
 */
@Api(tags = "AI 리포트")
@RestController
@RequestMapping("/api/ai-reports")
@RequiredArgsConstructor
public class AiReportController {

    // ai-service가 제공하는 AI 리포트 조회 인터페이스입니다.
    private final AiReportQueryClient aiReportQueryClient;
    private final AiReportEmailDeliveryClient aiReportEmailDeliveryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    /** 인증 사용자의 월간 AI 리포트를 가입 이메일로 PDF 첨부 발송합니다. */
    @ApiOperation("AI 리포트 이메일 PDF 내보내기")
    @PostMapping("/deliveries/email")
    public ResponseEntity<ApiResponse<AiReportEmailDeliveryResponse>> deliverAiReportByEmail(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestParam String yearMonth
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        AiReportEmailDeliveryResponse response = AiReportEmailDeliveryResponse.from(
                aiReportEmailDeliveryClient.deliver(userId, yearMonth)
        );
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                "AI 리포트를 이메일로 전송했습니다.",
                response
        ));
    }

    /**
     * 인증된 사용자의 특정 월 AI 리포트를 조회합니다.
     *
     * 요청 예시:
     * GET /api/ai-reports/2026-07
     */
    @ApiOperation("월별 AI 리포트 조회")
    @GetMapping("/{yearMonth}")
    public ResponseEntity<ApiResponse<AiReportResponse>> getAiReport(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable String yearMonth
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);

        // BFF는 인터페이스를 통해 ai-service에 리포트 조회를 요청합니다.
        AiReportSummary summary = aiReportQueryClient.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );

        // 내부 공통 DTO를 프론트엔드 전용 DTO로 변환합니다.
        AiReportResponse response = AiReportResponse.from(summary);

        // 기존 BFF 공통 응답 형식으로 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK.value(),
                        "AI 리포트 조회에 성공했습니다.",
                        response
                )
        );
    }

    /**
     * 인증된 사용자의 전체 AI 리포트 목록을 최신 연월순으로 조회합니다.
     *
     * 요청 예시:
     * GET /api/ai-reports
     *
     * JWT의 사용자 ID를 사용하므로 userId 쿼리 파라미터는 받지 않습니다.
     */
    @ApiOperation("전체 AI 리포트 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<AiReportListResponse>> getAiReports(
            @ApiParam(hidden = true) Authentication authentication
    ) {

        Long userId = authenticatedUserIdResolver.resolve(authentication);

        // BFF는 인터페이스를 통해 ai-service에 전체 AI 리포트 목록을 요청합니다.
        List<AiReportSummary> summaries = aiReportQueryClient.findAllByUserId(userId);

        // 공통 리포트 DTO 목록을 프론트엔드 목록 전용 DTO로 변환합니다.
        AiReportListResponse response = AiReportListResponse.from(summaries);

        // 기존 BFF 공통 응답 형식으로 목록 조회 결과를 반환합니다.
        return ResponseEntity.ok(
                ApiResponse.success(
                        HttpStatus.OK.value(),
                        "AI 리포트 목록 조회에 성공했습니다.",
                        response
                )
        );
    }
}
