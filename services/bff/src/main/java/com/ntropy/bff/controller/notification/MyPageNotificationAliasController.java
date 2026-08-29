package com.ntropy.bff.controller.notification;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.notification.response.NotificationsResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.notification.api.client.NotificationQueryClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

/**
 * 프론트가 /mypage/notifications로 호출하고 있어서 만든 별칭 경로.
 * 실제 조회 로직은 NotificationController와 동일한 NotificationQueryClient를 그대로 쓴다.
 * /api/mypage 통합 엔드포인트가 정식으로 생기면 이 컨트롤러는 지워도 된다.
 */
@Api(tags = "알림")
@RestController
@RequiredArgsConstructor
public class MyPageNotificationAliasController {

    private final NotificationQueryClient notificationQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @ApiOperation("알림 목록 조회 (마이페이지 경로 별칭)")
    @GetMapping("/api/mypage/notifications")
    public ApiResponse<NotificationsResponse> getMyPageNotifications(
            @ApiParam(hidden = true) Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(
                NotificationsResponse.from(notificationQueryClient.findNotifications(userId, page, size)));
    }
}
