package com.ntropy.bff.dto.notification.response;

import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.common.dto.account.PageSummary;
import com.ntropy.notification.api.dto.NotificationSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationsResponse {

    private List<NotificationResponse> notifications;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;

    public static NotificationsResponse from(PageSummary<NotificationSummary> page) {
        List<NotificationResponse> notifications = page.content().stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
        return new NotificationsResponse(
                notifications,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasNext()
        );
    }
}
