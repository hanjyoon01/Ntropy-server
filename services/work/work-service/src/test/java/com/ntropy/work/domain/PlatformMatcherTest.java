package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Platform;

class PlatformMatcherTest {

    // work-service-seed.sql PLATFORM 시드의 확정 deposit_name 15개(2026-08-14 기준).
    private static final List<Platform> SEED_PLATFORMS = List.of(
            platform(1L, "우아한청년들"),
            platform(2L, "쿠팡이츠정산"),
            platform(3L, "위대한상상"),
            platform(4L, "카카오모빌리티"),
            platform(5L, "GOOGLE"),
            platform(7L, "쿠팡-용역비"),
            platform(8L, "티맵모빌리티"),
            platform(9L, "CJ대한통운"),
            platform(10L, "로젠택배"),
            platform(11L, "생활연구소"),
            platform(12L, "유한회사미소"),
            platform(13L, "케어닥"),
            platform(14L, "펫피플"),
            platform(15L, "PAYPAL"),
            platform(16L, "네이버")
    );

    private static Platform platform(long id, String depositName) {
        return Platform.builder().platformId(id).depositName(depositName).build();
    }

    @Test
    @DisplayName("확정 시드의 deposit_name 15개는 각각 정확히 하나의 플랫폼에 매칭된다")
    void match_confirmedSeedDepositNames_returnsExactlyOneMatch() {
        List<String> confirmedDepositNames = List.of(
                "우아한청년들", "쿠팡이츠정산", "위대한상상", "카카오모빌리티", "GOOGLE",
                "쿠팡-용역비", "티맵모빌리티", "CJ대한통운", "로젠택배", "생활연구소",
                "유한회사미소", "케어닥", "펫피플", "PAYPAL", "네이버"
        );

        for (String depositName : confirmedDepositNames) {
            PlatformMatchResult result = PlatformMatcher.match(depositName, SEED_PLATFORMS);

            PlatformMatchResult.Matched matched = assertInstanceOf(PlatformMatchResult.Matched.class, result);
            assertEquals(depositName, matched.platform().getDepositName());
        }
    }

    @Test
    @DisplayName("환불·캐시백·예금이자 등 비잡 입금은 확정 시드 플랫폼과 매칭되지 않는다")
    void match_nonJobIncome_returnsNotMatched() {
        for (String counterpartyName : List.of("쿠팡", "김밥천국", "NH카드캐쉬백", "예금이자")) {
            PlatformMatchResult result = PlatformMatcher.match(counterpartyName, SEED_PLATFORMS);

            assertInstanceOf(PlatformMatchResult.NotMatched.class, result);
        }
    }

    @Test
    @DisplayName("정규화 후 정확히 하나의 PLATFORM과 일치하면 Matched를 반환한다")
    void match_singleMatch_returnsMatched() {
        List<Platform> platforms = List.of(
                platform(1L, "우아한형제들"),
                platform(2L, "쿠팡이츠")
        );

        PlatformMatchResult result = PlatformMatcher.match("우아한 형제들", platforms);

        PlatformMatchResult.Matched matched = assertInstanceOf(PlatformMatchResult.Matched.class, result);
        assertEquals(1L, matched.platform().getPlatformId());
    }

    @Test
    @DisplayName("일치하는 PLATFORM이 없으면 NotMatched를 반환한다")
    void match_noMatch_returnsNotMatched() {
        List<Platform> platforms = List.of(platform(1L, "우아한형제들"));

        PlatformMatchResult result = PlatformMatcher.match("알수없는입금처", platforms);

        assertInstanceOf(PlatformMatchResult.NotMatched.class, result);
    }

    @Test
    @DisplayName("정규화 후 동일해지는 PLATFORM이 여러 개면 Ambiguous를 반환한다")
    void match_multipleMatches_returnsAmbiguous() {
        List<Platform> platforms = List.of(
                platform(1L, "쿠팡이츠"),
                platform(2L, "쿠팡 이츠")
        );

        PlatformMatchResult result = PlatformMatcher.match("쿠팡이츠", platforms);

        PlatformMatchResult.Ambiguous ambiguous = assertInstanceOf(PlatformMatchResult.Ambiguous.class, result);
        assertEquals(2, ambiguous.candidates().size());
    }

    @Test
    @DisplayName("대소문자와 공백이 달라도 정규화되어 매칭된다")
    void match_normalizesBeforeComparing() {
        List<Platform> platforms = List.of(platform(1L, "Coupang Eats"));

        PlatformMatchResult result = PlatformMatcher.match("coupangeats", platforms);

        assertInstanceOf(PlatformMatchResult.Matched.class, result);
    }
}
