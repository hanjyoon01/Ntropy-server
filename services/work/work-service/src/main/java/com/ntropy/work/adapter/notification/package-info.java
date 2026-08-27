/**
 * work-service가 소유한 아웃바운드 어댑터 계층. port.notification의 인터페이스를 구현하며,
 * 내부적으로만 notification-service가 발행한 계약(common.client.NotificationCommandClient)을
 * 호출하고 결과를 버린다 - work는 알림 생성 성공 여부를 자신의 흐름 제어에 쓰지 않는다.
 */
package com.ntropy.work.adapter.notification;
