package com.ntropy.defense.api.dto.summary;

/** 방어모드 고정지출 유지 권고 상태. JSON과 Swagger에는 enum 이름으로 노출된다. */
public enum FixedExpenseMaintainStatus {
    NORMAL,
    DIFFICULT,
    REVIEW_SUSPENSION,
    UNDETERMINED
}
