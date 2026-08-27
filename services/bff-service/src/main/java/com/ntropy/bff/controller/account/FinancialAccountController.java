package com.ntropy.bff.controller.account;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.account.api.client.AccountQueryClient;
import com.ntropy.account.api.client.FinancialAccountCommandClient;
import com.ntropy.account.api.dto.AccountRegistrationSummary;
import com.ntropy.account.api.dto.AccountTransactionSummary;
import com.ntropy.account.api.dto.MyDataConnectionSummary;
import com.ntropy.bff.dto.account.request.AccountRegistrationRequest;
import com.ntropy.bff.dto.account.response.AccountActivationResponse;
import com.ntropy.bff.dto.account.response.AccountDeactivationResponse;
import com.ntropy.bff.dto.account.response.AccountsResponse;
import com.ntropy.bff.dto.account.response.BanksResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.dto.account.PageSummary;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;

@Api(tags = "금융계좌")
@RestController
@RequiredArgsConstructor
public class FinancialAccountController {

    private final AccountQueryClient accountQueryClient;
    private final FinancialAccountCommandClient financialAccountCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("지원 은행 목록 조회")
    @GetMapping("/api/banks")
    public ApiResponse<BanksResponse> getBanks() {
        return ApiResponse.success(new BanksResponse(financialAccountCommandClient.findSupportedBanks()));
    }

    @ApiOperation("CODEF 실제 금융 연동 상태 조회")
    @GetMapping("/api/mydata/status")
    public ApiResponse<MyDataConnectionSummary> getMyDataStatus(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(financialAccountCommandClient.findMyDataStatus(userId));
    }

    @ApiOperation("가상 또는 CODEF 계좌 등록")
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 201, message = "계좌 등록 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 값 오류"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 필요")
    })
    @PostMapping("/api/accounts")
    public ResponseEntity<ApiResponse<AccountRegistrationSummary>> registerAccount(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody AccountRegistrationRequest request
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        AccountRegistrationSummary result = financialAccountCommandClient.registerAccount(
                userId, request == null ? null : request.toCommand()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "계좌가 등록되었습니다.", result));
    }

    @ApiOperation("내 계좌 목록 조회")
    @GetMapping("/api/accounts")
    public ApiResponse<AccountsResponse> getAccounts(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(new AccountsResponse(accountQueryClient.findAccounts(userId)));
    }

    @ApiOperation("내 계좌 거래내역 조회")
    @GetMapping("/api/accounts/{accountId}/transactions")
    public ApiResponse<PageSummary<AccountTransactionSummary>> getTransactions(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(
                accountQueryClient.findTransactions(userId, accountId, startDate, endDate, page, size)
        );
    }

    @ApiOperation("내 계좌 비활성화")
    @PatchMapping("/api/accounts/{accountId}/deactivate")
    public ApiResponse<AccountDeactivationResponse> deactivateAccount(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long accountId
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        financialAccountCommandClient.deactivateAccount(userId, accountId);
        return ApiResponse.success(
                200, "계좌가 비활성화되었습니다.", new AccountDeactivationResponse(accountId, "INACTIVE")
        );
    }

    @ApiOperation("내 계좌 활성화")
    @PatchMapping("/api/accounts/{accountId}/activate")
    public ApiResponse<AccountActivationResponse> activateAccount(
            @ApiParam(hidden = true) Authentication authentication,
            @PathVariable Long accountId
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        financialAccountCommandClient.activateAccount(userId, accountId);
        return ApiResponse.success(
                200, "계좌가 활성화되었습니다.", new AccountActivationResponse(accountId, "ACTIVE")
        );
    }
}
