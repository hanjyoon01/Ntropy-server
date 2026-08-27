package com.ntropy.payment.domain;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
public enum PlanCode {

    BASIC(
            "Basic",
            0,
            List.of("대시보드", "캘린더", "소득 분석"),
            EnumSet.of(com.ntropy.common.domain.Feature.DASHBOARD, com.ntropy.common.domain.Feature.CALENDAR, com.ntropy.common.domain.Feature.INCOME_ANALYSIS)
    ),

    PRO(
            "Pro",
            4_900,
            List.of("Basic 모든 기능", "방어모드", "AI 리포트 제공"),
            EnumSet.of(com.ntropy.common.domain.Feature.DASHBOARD, com.ntropy.common.domain.Feature.CALENDAR, com.ntropy.common.domain.Feature.INCOME_ANALYSIS,
                    com.ntropy.common.domain.Feature.DEFENSE_MODE, com.ntropy.common.domain.Feature.AI_REPORT)
    );

    private final String displayName;
    private final int monthlyPrice;
    private final List<String> featureLabels;
    private final Set<com.ntropy.common.domain.Feature> features;

    PlanCode(String displayName, int monthlyPrice, List<String> featureLabels, Set<com.ntropy.common.domain.Feature> features) {
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.featureLabels = featureLabels;
        this.features = features;
    }

    public boolean supports(com.ntropy.common.domain.Feature feature) {
        return features.contains(feature);
    }
}