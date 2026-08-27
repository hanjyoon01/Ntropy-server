package com.ntropy.defense.service;

import com.ntropy.defense.domain.DefenseCause;
import com.ntropy.defense.domain.DefenseChecklistItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.ntropy.defense.domain.DefenseChecklistItem.*;

public final class DefenseChecklistCatalog {
    private static final Map<DefenseCause, List<DefenseChecklistItem>> ITEMS = new EnumMap<>(DefenseCause.class);

    static {
        ITEMS.put(DefenseCause.ACCIDENT_INJURY, Arrays.asList(
                RECORD_INCIDENT, CHECK_WORK_ACCIDENT_COVERAGE, VISIT_MEDICAL_PROVIDER));
        ITEMS.put(DefenseCause.ILLNESS, Arrays.asList(
                SAVE_MEDICAL_DOCUMENTS, REVIEW_HEALTH_COVERAGE, CHECK_ILLNESS_RETURN_TIMING));
        ITEMS.put(DefenseCause.PHYSICAL_RECOVERY, Arrays.asList(
                CHECK_FATIGUE_CONDITION, CHECK_MEDICAL_CARE_FOR_FATIGUE, PLAN_PHYSICAL_WORK_RETURN));
        ITEMS.put(DefenseCause.PLATFORM_RESTRICTION, Arrays.asList(
                SAVE_RESTRICTION_NOTICE, FILE_PLATFORM_APPEAL, SAVE_WORK_HISTORY));
        ITEMS.put(DefenseCause.MENTAL_HEALTH_RECOVERY, Arrays.asList(
                CHECK_MENTAL_CONDITION, CHECK_MENTAL_HEALTH_SUPPORT, PLAN_MENTAL_WORK_RETURN));
        ITEMS.put(DefenseCause.FAMILY_CARE_CRISIS, Arrays.asList(
                CHECK_CARE_WORK_TIME, CHECK_CARE_SUPPORT, ESTIMATE_CARE_PERIOD));
        ITEMS.put(DefenseCause.OTHER, Arrays.asList(
                CHECK_FIXED_COSTS, CHECK_WELFARE_SUPPORT, CHECK_PERSONAL_SCHEDULE_END));

        // 기존 데이터 조회 호환용 체크리스트
        ITEMS.put(DefenseCause.EQUIPMENT_FAILURE, Arrays.asList(
                ASSESS_REPAIR, CHECK_RENTAL_OPTION, CHECK_DAMAGE_COVERAGE, REVIEW_RETURN_DATE));
    }

    private DefenseChecklistCatalog() {
    }

    public static List<DefenseChecklistItem> findBy(DefenseCause cause) {
        return Collections.unmodifiableList(ITEMS.getOrDefault(cause, Collections.emptyList()));
    }
}
