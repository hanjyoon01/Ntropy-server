package com.ntropy.user.virtual;

import java.util.Locale;

/** 가상회원 (provider, provider_id) 규칙: provider=NTROPY_TEST, provider_id=virtual-user-{순번6자리}. */
public final class VirtualUserProviderId {

    public static final String PROVIDER = "NTROPY_TEST";
    public static final int MAX_ORDINAL = 999_999;

    private static final String PREFIX = "virtual-user-";
    private static final int ORDINAL_DIGITS = 6;

    private VirtualUserProviderId() {
    }

    public static String forOrdinal(int ordinal) {
        requireValidOrdinal(ordinal);
        return String.format(Locale.ROOT, "%s%06d", PREFIX, ordinal);
    }

    public static int parseOrdinal(String providerId) {
        if (providerId == null
                || providerId.length() != PREFIX.length() + ORDINAL_DIGITS
                || !providerId.startsWith(PREFIX)) {
            throw new IllegalArgumentException("가상회원 provider_id 형식이 올바르지 않습니다: " + providerId);
        }
        String ordinalText = providerId.substring(PREFIX.length());
        for (int index = 0; index < ordinalText.length(); index++) {
            if (!Character.isDigit(ordinalText.charAt(index))) {
                throw new IllegalArgumentException("가상회원 provider_id 형식이 올바르지 않습니다: " + providerId);
            }
        }
        int ordinal = Integer.parseInt(ordinalText);
        requireValidOrdinal(ordinal);
        return ordinal;
    }

    private static void requireValidOrdinal(int ordinal) {
        if (ordinal < 1 || ordinal > MAX_ORDINAL) {
            throw new IllegalArgumentException(
                    "가상회원 순번은 1.." + MAX_ORDINAL + " 범위여야 합니다: " + ordinal
            );
        }
    }
}
