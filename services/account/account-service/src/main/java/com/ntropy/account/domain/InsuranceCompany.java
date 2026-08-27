package com.ntropy.account.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 거래 설명(desc1~desc4)에 등장하는 보험사 법인명·축약명을 표준 보험사명으로 정규화하는 단일 registry.
 * 사보험과 국민건강보험·국민연금 등 공적 보험을 구분하지 않고 모두 포함한다.
 */
public enum InsuranceCompany {

    SAMSUNG_LIFE("삼성생명", "삼성생명보험", "삼성생명"),
    DB_INSURANCE("DB손보", "DB손해보험㈜", "DB손해보험", "DB손보"),
    KB_INSURANCE("KB손보", "KB손해보험", "KB손보"),
    HYUNDAI_INSURANCE("현대해상", "현대해상화재보험㈜", "현대해상화재보험", "현대해상"),
    KYOBO_LIFE("교보생명", "교보생명보험㈜", "교보생명보험", "교보생명"),
    HANWHA_LIFE("한화생명", "한화생명보험㈜", "한화생명보험", "한화생명"),
    NATIONAL_HEALTH_INSURANCE("국민건강보험", "국민건강보험공단", "국민건강보험"),
    NATIONAL_PENSION("국민연금", "국민연금공단", "국민연금");

    private final String standardName;
    private final List<String> normalizedAliases;

    InsuranceCompany(String standardName, String... aliases) {
        this.standardName = standardName;
        this.normalizedAliases = Arrays.stream(aliases)
                .map(InsuranceCompany::normalize)
                .toList();
    }

    public String getStandardName() {
        return standardName;
    }

    /** 정규화된 거래 설명에 별칭이 하나라도 포함되면 표준 보험사명을 반환한다. */
    public static Optional<String> matchStandardName(String rawDescription) {
        String normalizedDescription = normalize(rawDescription);
        if (normalizedDescription.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(company -> company.normalizedAliases.stream().anyMatch(normalizedDescription::contains))
                .map(InsuranceCompany::getStandardName)
                .findFirst();
    }

    /** 공백·기호·법인 표기(㈜ 등)를 제거하고 영문 대소문자를 통일한다. 한글·영문·숫자만 남긴다. */
    private static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[^\\p{IsHangul}a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
