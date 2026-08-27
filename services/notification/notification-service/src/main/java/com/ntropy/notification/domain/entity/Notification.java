package com.ntropy.notification.domain.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    private Long notificationId;       // notification_id
    private Long userId;               // user_id
    private String eventId;            // event_id - 중복 생성 방지용 멱등성 키
    private String notificationType;   // notification_type
    private String title;              // title
    private String body;               // body
    private LocalDateTime readAt;      // read_at
    private LocalDateTime createdAt;   // created_at
    private LocalDateTime deletedAt;   // deleted_at
}
