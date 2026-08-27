package com.ntropy.account.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.ntropy.account.config.BirthDateEncryptionProperties;
import com.ntropy.account.security.BirthDateCipher.EncryptedBirthDate;

class BirthDateCipherTest {

    private static final String VALID_KEY_BASE64 = "EnZnHF6ULrhAjwHSJs5+2lizbiv7BHiB+5sZ4YIKEmc=";

    @Test
    void encryptsAndDecryptsBackToOriginalBirthDate() {
        BirthDateCipher cipher = new BirthDateCipher(new BirthDateEncryptionProperties(VALID_KEY_BASE64, 1));

        EncryptedBirthDate encrypted = cipher.encrypt("19900101");
        String decrypted = cipher.decrypt(encrypted.ciphertextBase64(), encrypted.ivBase64());

        assertEquals("19900101", decrypted);
        assertEquals(1, encrypted.keyVersion());
    }

    @Test
    void ciphertextNeverContainsThePlainBirthDate() {
        BirthDateCipher cipher = new BirthDateCipher(new BirthDateEncryptionProperties(VALID_KEY_BASE64, 1));

        EncryptedBirthDate encrypted = cipher.encrypt("19900101");

        assertNotEquals("19900101", encrypted.ciphertextBase64());
        assertFalse(
                encrypted.ciphertextBase64().contains("19900101"),
                "암호문에 평문 생년월일이 그대로 노출되면 안 됩니다"
        );
    }

    @Test
    void usesAFreshIvOnEveryEncryptionSoSameInputProducesDifferentCiphertext() {
        BirthDateCipher cipher = new BirthDateCipher(new BirthDateEncryptionProperties(VALID_KEY_BASE64, 1));

        EncryptedBirthDate first = cipher.encrypt("19900101");
        EncryptedBirthDate second = cipher.encrypt("19900101");

        assertNotEquals(first.ivBase64(), second.ivBase64());
        assertNotEquals(first.ciphertextBase64(), second.ciphertextBase64());
        // 그래도 둘 다 올바르게 복호화돼야 한다.
        assertEquals("19900101", cipher.decrypt(first.ciphertextBase64(), first.ivBase64()));
        assertEquals("19900101", cipher.decrypt(second.ciphertextBase64(), second.ivBase64()));
    }

    @Test
    void decryptionFailsWithWrongKey() {
        BirthDateCipher encryptingCipher = new BirthDateCipher(new BirthDateEncryptionProperties(VALID_KEY_BASE64, 1));
        String otherKeyBase64 = "NfKkGC76DP+C6KKxj6mhSMqvIOmM2NZVQYcUhXTP+9k=";
        BirthDateCipher decryptingCipher = new BirthDateCipher(new BirthDateEncryptionProperties(otherKeyBase64, 1));

        EncryptedBirthDate encrypted = encryptingCipher.encrypt("19900101");

        assertThrows(IllegalStateException.class,
                () -> decryptingCipher.decrypt(encrypted.ciphertextBase64(), encrypted.ivBase64()));
    }

    @Test
    void rejectsKeyThatIsNotExactly32Bytes() {
        assertThrows(IllegalStateException.class,
                () -> new BirthDateEncryptionProperties("dG9vc2hvcnQ=", 1));
    }

    @Test
    void rejectsMissingKey() {
        assertThrows(IllegalStateException.class,
                () -> new BirthDateEncryptionProperties("", 1));
        assertThrows(IllegalStateException.class,
                () -> new BirthDateEncryptionProperties(null, 1));
    }

    @Test
    void rejectsNonPositiveKeyVersion() {
        assertThrows(IllegalStateException.class,
                () -> new BirthDateEncryptionProperties(VALID_KEY_BASE64, 0));
    }
}
