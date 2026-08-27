package com.ntropy.bff.controller.subscription;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.subscription.request.PaymentMethodUpdateRequest;
import com.ntropy.bff.dto.subscription.request.SubscriptionInitRequest;
import com.ntropy.bff.dto.subscription.response.*;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.payment.api.client.SubscriptionCommandClient;
import com.ntropy.payment.api.client.SubscriptionQueryClient;
import com.ntropy.payment.api.dto.PlanSummary;
import com.ntropy.payment.api.dto.PaymentConfigSummary;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "구독")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionQueryClient subscriptionQueryClient;
    private final SubscriptionCommandClient subscriptionCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("구독 플랜 목록 조회")
    @GetMapping("/plans")
    public ApiResponse<PlansResponse> getPlans() {
        PlansResponse response = new PlansResponse(subscriptionQueryClient.getPlans());
        return ApiResponse.success(response);
    }

    @ApiOperation("결제 설정 조회")
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<PaymentConfigSummary>> getPaymentConfig(
            @ApiParam(hidden = true) Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        PaymentConfigSummary config = subscriptionQueryClient.getPaymentConfig(userId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(config));
    }

    @ApiOperation("내 구독 조회")
    @GetMapping
    public ApiResponse<SubscriptionResponse> getMySubscription(
            @ApiParam(hidden = true) Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SubscriptionResponse response = SubscriptionResponse.from(subscriptionQueryClient.getMySubscription(userId));
        return ApiResponse.success(response);
    }


    //최초결제 + 빌링키 발급요청 (SUBSCRIPTION02_F01).

    @ApiOperation("구독 시작(최초 결제 + 빌링키 발급)")
    @PostMapping
    public ApiResponse<SubscriptionResponse> initSubscription(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody SubscriptionInitRequest request
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SubscriptionResponse response = SubscriptionResponse.from(
                subscriptionCommandClient.initSubscription(userId, request.getBillingKey())
        );
        return ApiResponse.success(response);
    }

    @ApiOperation("결제수단 변경")
    @PostMapping("/payment-method")
    public ApiResponse<SubscriptionResponse> updatePaymentMethod(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestBody PaymentMethodUpdateRequest request
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SubscriptionResponse response = SubscriptionResponse.from(
                subscriptionCommandClient.updatePaymentMethod(userId, request.getBillingKey())
        );
        return ApiResponse.success(response);
    }

    @ApiOperation("PortOne 웹훅 수신")
    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature,
            @RequestBody String rawBody
    ) {
        boolean verified = subscriptionCommandClient.receiveWebhook(webhookId, webhookTimestamp, webhookSignature, rawBody);
        return verified ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ApiOperation("구독 해지 예약")
    @PostMapping("/cancel")
    public ApiResponse<SubscriptionResponse> cancelSubscription(
            @ApiParam(hidden = true) Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SubscriptionResponse response = SubscriptionResponse.from(subscriptionCommandClient.cancelSubscription(userId));
        return ApiResponse.success(response);
    }

    @ApiOperation("구독 해지 예약 취소")
    @DeleteMapping("/cancel")
    public ApiResponse<SubscriptionResponse> revokeCancel(
            @ApiParam(hidden = true) Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SubscriptionResponse response = SubscriptionResponse.from(subscriptionCommandClient.revokeCancel(userId));
        return ApiResponse.success(response);
    }

    @ApiOperation("결제 내역 조회")
    @GetMapping("/payments")
    public ApiResponse<PaymentHistoryResponse> getPaymentHistory(
            @ApiParam(hidden = true) Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        PaymentHistoryResponse response = new PaymentHistoryResponse(
                subscriptionQueryClient.getPaymentHistory(userId).stream()
                        .map(PaymentHistoryItemResponse::from)
                        .collect(Collectors.toList())
        );
        return ApiResponse.success(response);
    }

    @ApiOperation("구독 관리 화면용 정보 조회")
    @GetMapping("/management")
    public ApiResponse<SubscriptionManagementResponse> getSubscriptionManagement(
            @ApiParam(hidden = true) Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        SubscriptionResponse currentSubscription = SubscriptionResponse.from(subscriptionQueryClient.getMySubscription(userId));
        List<PlanSummary> availablePlans = subscriptionQueryClient.getPlans();

        return ApiResponse.success(new SubscriptionManagementResponse(currentSubscription, availablePlans));
    }
}
