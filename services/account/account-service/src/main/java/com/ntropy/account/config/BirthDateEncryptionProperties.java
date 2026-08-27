package com.ntropy.account.config;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * birthDate 저장 암호화(AES-256-GCM) 키 정책 (이슈 #158).
 * 키는 소스 코드나 DB가 아닌 환경변수·외부 secret({@code birth-date-local.properties}, git 제외)에서
 * 주입하며, 누락되거나 길이가 올바르지 않으면 애플리케이션 시작 단계에서 실패한다.
 */
@Getter
@Component
public class BirthDateEncryptionProperties {

    private static final int REQUIRED_KEY_BYTES = 32; // AES-256

    private final byte[] keyBytes;
    private final int keyVersion;

    public BirthDateEncryptionProperties(
            @Value("${birth-date.encryption.key-base64:}") String keyBase64,
            @Value("${birth-date.encryption.key-version:1}") int keyVersion
    ) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException(
                    "birth-date.encryption.key-base64가 설정되지 않았습니다. "
                            + "birth-date.properties.example을 birth-date-local.properties로 복사해 채우세요."
            );
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("birth-date.encryption.key-base64가 올바른 Base64 값이 아닙니다", e);
        }
        if (decoded.length != REQUIRED_KEY_BYTES) {
            throw new IllegalStateException(
                    "birth-date.encryption.key-base64는 " + REQUIRED_KEY_BYTES + "바이트(AES-256)여야 합니다. "
                            + "실제 길이=" + decoded.length
            );
        }
        if (keyVersion <= 0) {
            throw new IllegalStateException("birth-date.encryption.key-version은 양수여야 합니다");
        }
        this.keyBytes = decoded;
        this.keyVersion = keyVersion;
    }
}
