package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformNameNormalizerTest {

    // work-service-seed.sql PLATFORM 시드의 확정 deposit_name 15개(2026-08-14 기준).
    private static final List<String> CONFIRMED_SEED_DEPOSIT_NAMES = List.of(
            "우아한청년들", "쿠팡이츠정산", "위대한상상", "카카오모빌리티", "GOOGLE",
            "쿠팡-용역비", "티맵모빌리티", "CJ대한통운", "로젠택배", "생활연구소",
            "유한회사미소", "케어닥", "펫피플", "PAYPAL", "네이버"
    );

    @Test
    @DisplayName("확정 시드의 deposit_name 15개는 정규화 후에도 서로 중복되지 않는다")
    void normalize_confirmedSeedDepositNamesStayDistinct() {
        Set<String> normalized = CONFIRMED_SEED_DEPOSIT_NAMES.stream()
                .map(PlatformNameNormalizer::normalize)
                .collect(Collectors.toSet());

        assertEquals(CONFIRMED_SEED_DEPOSIT_NAMES.size(), normalized.size());
    }

    @Test
    @DisplayName("공백이 포함된 거래명은 공백이 제거되어 정규화된다")
    void normalize_removesWhitespace() {
        assertEquals("우아한형제들", PlatformNameNormalizer.normalize("우아한 형제들"));
    }

    @Test
    @DisplayName("영문 거래명은 소문자로 정규화된다")
    void normalize_lowercasesEnglish() {
        assertEquals("coupangeats", PlatformNameNormalizer.normalize("Coupang Eats"));
    }

    @Test
    @DisplayName("괄호 등 기호가 포함된 거래명은 기호가 제거되어 정규화된다")
    void normalize_removesSymbols() {
        assertEquals("쿠팡이츠", PlatformNameNormalizer.normalize("쿠팡(이츠)"));
    }

    @Test
    @DisplayName("null 입력은 빈 문자열로 정규화된다")
    void normalize_nullReturnsEmptyString() {
        assertEquals("", PlatformNameNormalizer.normalize(null));
    }
}
