package com.ntropy.bff.controller.internal;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.work.api.client.SettlementBatchCommandClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

/**
 * [테스트용] 정산 배치(SettlementService)를 특정 사용자·날짜로 즉시 실행해보는 컨트롤러.
 * 원래 정산은 SettlementScheduler가 매일 새벽 1시 30분에만 자동 실행하는데,
 * 그걸 기다리지 않고 로컬/개발 환경에서 바로 결과를 확인하려고 만든 것.
 * 검증 끝나면 이 클래스와 SecurityConfig의 "/internal/test/**" permitAll 줄, common의
 * SettlementBatchCommandClient, work-service의 LocalSettlementBatchCommandClient까지
 * 같이 지울 것.
 */
@Api(tags = "[테스트용] 정산 배치 수동 실행")
@RestController
@RequestMapping("/internal/test/settlement")
@RequiredArgsConstructor
public class SettlementTestController {

    private final SettlementBatchCommandClient settlementBatchCommandClient;

    @ApiOperation("특정 사용자의 특정 날짜 입금 거래를 지금 바로 정산 매칭 시도한다")
    @GetMapping("/run")
    public ApiResponse<String> run(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        boolean created = settlementBatchCommandClient.runForDate(userId, date);
        String message = created
                ? "새 SETTLEMENT가 생성됐습니다."
                : "생성된 SETTLEMENT가 없습니다 (그 날짜에 매칭할 거래가 없거나, 이미 처리된 거래뿐입니다).";
        return ApiResponse.success(200, message, message);
    }
}
