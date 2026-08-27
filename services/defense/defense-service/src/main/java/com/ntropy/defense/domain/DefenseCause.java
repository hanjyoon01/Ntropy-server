package com.ntropy.defense.domain;

import lombok.Getter;

@Getter
public enum DefenseCause {
    ACCIDENT_INJURY("HEALTH", "사고부상", null, true),
    ILLNESS("HEALTH", "질병치료", null, true),
    PHYSICAL_RECOVERY("HEALTH", "신체피로", null, true),
    PLATFORM_RESTRICTION("PLATFORM", "계정정지", null, true),
    FAMILY_CARE_CRISIS("FAMILY", "육아돌봄", null, true),
    OTHER("ETC", "기타", "근무가 어려운 상황을 기록하고 복귀 계획을 확인해 주세요.", true),

    // 기존 기록 조회 호환용이며 신규 방어모드 사유 목록에는 노출하지 않는다.
    MENTAL_HEALTH_RECOVERY("HEALTH", "심리회복", null, false),
    EQUIPMENT_FAILURE("EQUIPMENT", "이동수단·업무장비 문제", null, false);

    private final String causeGroup;
    private final String causeName;
    private final String guideMessage;
    private final boolean selectable;

    DefenseCause(String causeGroup, String causeName, String guideMessage, boolean selectable) {
        this.causeGroup = causeGroup;
        this.causeName = causeName;
        this.guideMessage = guideMessage;
        this.selectable = selectable;
    }
}
