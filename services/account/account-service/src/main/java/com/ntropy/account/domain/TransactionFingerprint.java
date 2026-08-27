package com.ntropy.account.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * CODEF가 고유 거래 ID를 제공하지 않는 거래내역의 중복 판별용 SHA-256 fingerprint를 만든다.
 */
public final class TransactionFingerprint {

    private static final char SEPARATOR = '\u001f';
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TransactionFingerprint() {
    }

    public static String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder();
            for (Object value : values) {
                if (canonical.length() > 0) {
                    canonical.append(SEPARATOR);
                }
                canonical.append(normalize(value));
            }
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    private static String normalize(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof BigDecimal amount) {
            return amount.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalTime time) {
            return time.format(HHMMSS);
        }
        return value.toString().trim();
    }
}
