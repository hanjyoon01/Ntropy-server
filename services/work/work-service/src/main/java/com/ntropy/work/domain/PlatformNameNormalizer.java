package com.ntropy.work.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 입금 거래명과 PLATFORM.deposit_name을 비교하기 위한 정규화 유틸.
 * NFKC 변환, 영문 소문자화, 공백/기호 제거를 적용한다 (숫자·한글·영문만 남김).
 */
public final class PlatformNameNormalizer {

    private PlatformNameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String nfkc = Normalizer.normalize(name, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
    }
}
