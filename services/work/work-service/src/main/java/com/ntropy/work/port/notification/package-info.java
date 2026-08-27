/**
 * work-service가 소유한, notification-service에 대한 아웃바운드 포트 계층. work의 도메인
 * 코어는 notification-service의 계약(common.client.NotificationCommandClient)과 그 DTO를
 * 직접 참조하지 않고 이 포트만 알면 된다. 실제 호출은 adapter.notification이 담당한다.
 * work는 알림 생성만 필요로 하므로(읽음처리·삭제는 사용자 액션이라 bff가 직접 처리) create만
 * 포트로 노출한다.
 */
package com.ntropy.work.port.notification;
