package com.ntropy.bff.controller.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.account.api.client.DailyFinancialSyncClient;
import com.ntropy.account.api.dto.DailyFinancialSyncResult;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

/**
 * [테스트용] CODEF 일일 증분 동기화 배치를 특정 사용자·날짜로 즉시 실행해보는 컨트롤러 (이슈 #199).
 * 원래 CODEF 동기화는 DailyFinancialSyncScheduler가 매일 새벽 1시에만 자동 실행하는데,
 * 그걸 기다리지 않고 로컬/개발 환경에서 바로 결과를 확인하려고 만든 것.
 * 스케줄러가 실제로 타는 것과 동일한 진입점(DailyFinancialSyncClient, lease 획득/스킵/완료 로직 포함)을
 * 그대로 재사용하므로, 같은 (userId, date)로 여러 번 호출해도 RUNNING 상태만 아니면 재트리거된다.
 * 검증 끝나면 이 클래스와 SecurityConfig의 "/internal/test/**" permitAll 줄(SettlementTestController와
 * 공용이므로 그쪽도 지워질 때만) 정리 여부를 같이 확인할 것.
 */
@Api(tags = "[테스트용] CODEF 동기화 배치 수동 실행")
@RestController
@RequestMapping("/internal/test/codef-sync")
@RequiredArgsConstructor
public class CodefSyncTestController {

    private final DailyFinancialSyncClient dailyFinancialSyncClient;

    @ApiOperation("특정 사용자의 CODEF 연동을 특정 영업일 기준으로 지금 바로 동기화한다")
    @GetMapping("/run")
    public ApiResponse<DailyFinancialSyncResult> run(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        DailyFinancialSyncResult result = dailyFinancialSyncClient.synchronize(
                DailyFinancialSyncProvider.CODEF, List.of(userId), date
        );
        return ApiResponse.success(200, "CODEF 동기화 배치를 실행했습니다. executionStatus=" + result.executionStatus(), result);
    }
}
