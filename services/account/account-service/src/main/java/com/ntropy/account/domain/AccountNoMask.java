package com.ntropy.account.domain;

/**
 * 계좌번호 원문을 DB에 남기지 않고 끝 네 자리만 표시할 수 있는 값으로 변환한다.
 */
public final class AccountNoMask {

    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final String MASK_PREFIX = "****";

    private AccountNoMask() {
    }

    public static String mask(String rawAccountNo) {
        if (rawAccountNo == null || rawAccountNo.isBlank()) {
            throw new IllegalArgumentException("계좌번호가 필요합니다");
        }
        String normalized = rawAccountNo.replaceAll("[^0-9A-Za-z]", "");
        if (normalized.length() <= VISIBLE_SUFFIX_LENGTH) {
            return MASK_PREFIX;
        }
        return MASK_PREFIX + normalized.substring(normalized.length() - VISIBLE_SUFFIX_LENGTH);
    }
}
