package com.ntropy.bff.controller.internal;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient;
import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient.BatchResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

/**
 * [테스트용] 특정 사용자·날짜의 가상 정산 입금 생성과 후속 정산 매칭을 즉시 실행한다.
 * 검증이 끝나면 기존 SettlementTestController, CodefSyncTestController와 함께 삭제할 대상이다.
 */
@Api(tags = "[테스트용] 가상 정산 입금 배치 수동 실행")
@RestController
@RequestMapping("/internal/test/virtual-settlement")
@RequiredArgsConstructor
public class VirtualSettlementDepositTestController {

    private final VirtualSettlementDepositBatchCommandClient batchCommandClient;

    @ApiOperation("특정 사용자의 가상 정산 입금을 생성하고 정산 매칭까지 지금 바로 실행한다")
    @GetMapping("/run")
    public ApiResponse<BatchResult> run(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        BatchResult result = batchCommandClient.runForDate(userId, date);
        String message = String.format(
                "가상 정산 입금 배치를 실행했습니다. 생성 입금 %d건, 매칭 SETTLEMENT %d건",
                result.createdDepositCount(), result.matchedSettlementCount());
        return ApiResponse.success(200, message, result);
    }
}
