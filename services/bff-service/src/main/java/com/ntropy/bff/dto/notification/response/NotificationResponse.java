package com.ntropy.bff.dto.notification.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.notification.api.dto.NotificationSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private Long notificationId;
    private String notificationType;
    private String title;
    private String body;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime readAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    public static NotificationResponse from(NotificationSummary summary) {
        return new NotificationResponse(
                summary.notificationId(),
                summary.notificationType(),
                summary.title(),
                summary.body(),
                summary.readAt(),
                summary.createdAt()
        );
    }
}
