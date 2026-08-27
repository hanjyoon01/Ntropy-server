package com.ntropy.account.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 계좌 원문 번호를 저장하지 않고 중복 판별에만 쓰는 해시를 만든다.
 * 같은 기관 안에서도 계좌번호가 우연히 겹치지 않도록 기관코드를 함께 해시한다.
 */
public final class AccountNoHash {

    private AccountNoHash() {
    }

    public static String hash(String organizationCode, String rawAccountNo) {
        if (organizationCode == null || organizationCode.isBlank()) {
            throw new IllegalArgumentException("기관코드가 필요합니다");
        }
        if (rawAccountNo == null || rawAccountNo.isBlank()) {
            throw new IllegalArgumentException("계좌번호가 필요합니다");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    (organizationCode + ":" + rawAccountNo).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }
}
