package com.ntropy.notification.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.notification.domain.entity.Notification;

@Mapper
public interface NotificationMapper {

    void insertNotification(Notification notification);

    Optional<Notification> findByEventId(@Param("eventId") String eventId);

    Optional<Notification> findByIdAndUserId(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    List<Notification> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    long countByUserId(@Param("userId") Long userId);

    long countUnreadByUserId(@Param("userId") Long userId);

    void markAsRead(@Param("notificationId") Long notificationId);

    void softDelete(@Param("notificationId") Long notificationId);
}
