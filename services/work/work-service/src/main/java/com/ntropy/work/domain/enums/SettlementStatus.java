package com.ntropy.work.domain.enums;

/**
 * WORK_LOG의 정산 상태. work-service 내부에서만 세팅하는 값(클라이언트 입력 필드 아님)이라
 * 현재는 common DTO로 노출하지 않는다.
 */
public enum SettlementStatus {
    NONE,
    PENDING,
    PARTIAL,
    COMPLETED
}
