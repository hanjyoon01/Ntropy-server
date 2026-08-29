package com.ntropy.bff.controller.defense;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.defense.request.DefenseModeEnterRequest;
import com.ntropy.bff.dto.defense.request.DefenseModeReleaseRequest;
import com.ntropy.bff.dto.defense.response.DefenseCausesResponse;
import com.ntropy.bff.dto.defense.response.DefenseCalendarResponse;
import com.ntropy.bff.dto.defense.response.DefenseModeResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.defense.api.client.DefenseModeCommandClient;
import com.ntropy.defense.api.client.DefenseModeQueryClient;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Api(tags = "방어모드")
@RestController
@RequestMapping("/api/defense")
@RequiredArgsConstructor
public class DefenseModeController {
    private final DefenseModeCommandClient defenseModeCommandClient;
    private final DefenseModeQueryClient defenseModeQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("방어모드 발동 원인 목록 조회")
    @GetMapping("/causes")
    public ApiResponse<DefenseCausesResponse> getCauses() {
        return ApiResponse.success(new DefenseCausesResponse(defenseModeQueryClient.getCauses()));
    }

    @ApiOperation("방어모드 진입 또는 예약")
    @PostMapping
    public ResponseEntity<ApiResponse<DefenseModeResponse>> enter(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody DefenseModeEnterRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        DefenseModeResponse response = DefenseModeResponse.from(defenseModeCommandClient.enter(request.toCommand(userId)));
        String message = "SCHEDULED".equals(response.getStatus())
                ? "방어모드가 예약되었습니다."
                : "방어모드가 시작되었습니다.";
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(HttpStatus.CREATED.value(), message, response));
    }

    @ApiOperation("현재 또는 예약된 방어모드 조회")
    @GetMapping("/active")
    public ApiResponse<DefenseModeResponse> getCurrent(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(DefenseModeResponse.from(defenseModeQueryClient.getCurrent(userId)));
    }

    @ApiOperation("캘린더용 방어모드 기간 조회")
    @GetMapping("/calendar")
    public ApiResponse<DefenseCalendarResponse> getCalendar(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        YearMonth targetMonth = YearMonth.of(year, month);
        return ApiResponse.success(new DefenseCalendarResponse(
                defenseModeQueryClient.getCalendarPeriods(
                        userId, targetMonth.atDay(1), targetMonth.atEndOfMonth())));
    }

    @ApiOperation("방어모드 해제")
    @PatchMapping("/{defenseId}/return")
    public ApiResponse<DefenseModeResponse> release(@PathVariable Long defenseId,
                                                     @ApiParam(hidden = true) Authentication authentication,
                                                     @RequestBody DefenseModeReleaseRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(200, "방어모드가 해제되었습니다.",
                DefenseModeResponse.from(defenseModeCommandClient.release(defenseId, request.toCommand(userId))));
    }
}
