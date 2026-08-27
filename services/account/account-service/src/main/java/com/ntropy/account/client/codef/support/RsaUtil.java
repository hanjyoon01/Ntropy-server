package com.ntropy.account.client.codef.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * CODEF 공개키(RSA)로 평문을 암호화한다. 계정 등록(account/create) API의 password 필드에 사용.
 * 알고리즘/패딩은 CODEF 공식 Java SDK(github.com/codef-io/codef-java RSAUtil)와 동일하게 맞춤.
 */
public final class RsaUtil {

    private static final String ALGORITHM = "RSA";

    private RsaUtil() {
    }

    public static String encryptWithPublicKey(String plainText, String base64PublicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("CODEF RSA 암호화 실패", e);
        }
    }
}
