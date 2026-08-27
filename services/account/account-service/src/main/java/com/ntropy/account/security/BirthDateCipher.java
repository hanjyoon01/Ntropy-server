package com.ntropy.account.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ntropy.account.config.BirthDateEncryptionProperties;

import lombok.RequiredArgsConstructor;

/**
 * 기업·국민은행 birthDate를 AES-256-GCM으로 암·복호화한다 (이슈 #158).
 * CODEF 요청 비밀번호 전송에 쓰는 {@link com.ntropy.account.client.codef.support.RsaUtil}과는
 * 목적이 다르므로(전송용 공개키 암호화 vs 저장용 대칭키 암호화) 재사용하지 않는다.
 * 암·복호화 책임을 이 컴포넌트 안에 격리해 평문 birthDate가 밖으로 새지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class BirthDateCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final BirthDateEncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedBirthDate encrypt(String plainBirthDate) {
        if (plainBirthDate == null || plainBirthDate.isBlank()) {
            throw new IllegalArgumentException("암호화할 생년월일이 필요합니다");
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainBirthDate.getBytes(StandardCharsets.UTF_8));
            return new EncryptedBirthDate(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv),
                    properties.getKeyVersion()
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("생년월일 암호화에 실패했습니다", e);
        }
    }

    public String decrypt(String ciphertextBase64, String ivBase64) {
        if (ciphertextBase64 == null || ciphertextBase64.isBlank()
                || ivBase64 == null || ivBase64.isBlank()) {
            throw new IllegalStateException("복호화할 생년월일 암호문 또는 IV가 없습니다");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("생년월일 복호화에 실패했습니다", e);
        }
    }

    private SecretKeySpec secretKey() {
        return new SecretKeySpec(properties.getKeyBytes(), "AES");
    }

    /** 저장용 암호문·IV·암호화 당시 key version. 평문 생년월일은 담지 않는다. */
    public record EncryptedBirthDate(String ciphertextBase64, String ivBase64, int keyVersion) {
    }
}
