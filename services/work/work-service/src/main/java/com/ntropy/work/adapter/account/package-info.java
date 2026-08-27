/**
 * work-service가 소유한 아웃바운드 어댑터 계층. port.account의 인터페이스를 구현하며,
 * 내부적으로만 account-service가 발행한 계약(common.client.*)을 호출하고 work의 포트
 * 타입으로 변환한다 - account의 계약 변경이 이 계층 밖(work 도메인 코어)으로 새어나가지
 * 않도록 막는 경계다.
 */
package com.ntropy.work.adapter.account;
