/**
 * work-service가 소유한 아웃바운드 포트 계층. account-service로부터 필요한 데이터/기능을
 * work의 언어로 정의한다. 실제 호출은 adapter.account가 담당하며, work의 도메인 코어는
 * 이 패키지의 인터페이스와 값 타입만 알면 된다 (account의 계약·DTO를 직접 참조하지 않는다).
 */
package com.ntropy.work.port.account;
