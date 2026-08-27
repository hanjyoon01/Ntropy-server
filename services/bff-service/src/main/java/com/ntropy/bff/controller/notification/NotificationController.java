package com.ntropy.bff.controller.notification;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.notification.request.PushSubscriptionRequest;
import com.ntropy.bff.dto.notification.response.NotificationsResponse;
import com.ntropy.bff.dto.notification.response.UnreadCountResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.notification.api.client.NotificationCommandClient;
import com.ntropy.notification.api.client.NotificationQueryClient;
import com.ntropy.notification.api.client.PushSubscriptionCommandClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@Api(tags = "알림")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryClient notificationQueryClient;
    private final NotificationCommandClient notificationCommandClient;
    private final PushSubscriptionCommandClient pushSubscriptionCommandClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("웹푸시 구독에 필요한 VAPID 공개키 조회")
    @GetMapping("/push-public-key")
    public ApiResponse<String> getPushPublicKey() {
        return ApiResponse.success(pushSubscriptionCommandClient.getVapidPublicKey());
    }

    @ApiOperation("웹푸시 구독 등록")
    @PostMapping("/push-subscriptions")
    public ApiResponse<Void> subscribePush(@ApiParam(hidden = true) Authentication authentication,
                                            @RequestBody PushSubscriptionRequest request) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        pushSubscriptionCommandClient.subscribe(request.toCommand(userId));
        return ApiResponse.success(200, "웹푸시 구독이 등록되었습니다.", null);
    }

    @ApiOperation("웹푸시 구독 해제")
    @DeleteMapping("/push-subscriptions")
    public ApiResponse<Void> unsubscribePush(@RequestParam String endpoint) {
        pushSubscriptionCommandClient.unsubscribe(endpoint);
        return ApiResponse.success(200, "웹푸시 구독이 해제되었습니다.", null);
    }

    @ApiOperation("알림 목록 조회")
    @GetMapping
    public ApiResponse<NotificationsResponse> getNotifications(@ApiParam(hidden = true) Authentication authentication,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(
                NotificationsResponse.from(notificationQueryClient.findNotifications(userId, page, size)));
    }

    @ApiOperation("읽지 않은 알림 개수 조회")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@ApiParam(hidden = true) Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(new UnreadCountResponse(notificationQueryClient.countUnread(userId)));
    }

    @ApiOperation("알림 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(@ApiParam(hidden = true) Authentication authentication,
                                         @PathVariable Long notificationId) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        notificationCommandClient.markAsRead(userId, notificationId);
        return ApiResponse.success(200, "알림을 읽음 처리했습니다.", null);
    }

    @ApiOperation("알림 삭제")
    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(@ApiParam(hidden = true) Authentication authentication,
                                     @PathVariable Long notificationId) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        notificationCommandClient.delete(userId, notificationId);
        return ApiResponse.success(200, "알림을 삭제했습니다.", null);
    }
}
