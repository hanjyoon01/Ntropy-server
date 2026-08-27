package com.ntropy.work.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 온보딩 단계에서 입금 내역 매칭 결과로 산출된 잡 등록 후보.
 * 배달 카테고리는 여러 플랫폼(배민, 쿠팡이츠 등)을 동시에 운영하는 경우가 많아
 * 하나의 후보로 묶어서 보여준다 (platforms에 여러 건 포함).
 * categoryName은 배달처럼 묶인 후보에만 채워지고, 단일 플랫폼 후보는 null이다
 * (단일 후보는 플랫폼명을 그대로 표시명으로 쓰면 되므로).
 * 매칭 결과가 실제로 맞는지는 배달 여부·플랫폼 개수와 무관하게 모든 후보에 대해
 * 사용자 확인이 필요하므로, 확인 필요 여부를 나타내는 필드는 두지 않는다.
 */
public record JobCandidate(
        Long categoryId,
        String categoryName,
        List<PlatformMatch> platforms,
        int settlementCount,
        BigDecimal totalAmount
) {
}
