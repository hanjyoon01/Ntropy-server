package com.ntropy.account.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.account.client.codef.CodefConnectionClient;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;
import com.ntropy.account.security.BirthDateCipher.EncryptedBirthDate;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 계정을 등록하고 발급된 connectedId를 사용자와 연결한다.
 * 같은 사용자의 connectedId가 있으면 새 기관은 추가하고, 이미 등록된 기관은 요청 시 전달받은
 * 최신 인증정보로 수정한다 (동시 요청 경합 방지는 후속 이슈 범위).
 * 기업·국민은행처럼 birthDate가 필요한 은행은 등록 성공 후 암호화해 저장한다(이슈 #158) —
 * 일일 증분 동기화 시 다시 필요하기 때문이다. birthDate가 필요 없는 은행을 추가·수정할 때는
 * 이미 저장된 다른 은행의 birthDate 암호문을 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CodefConnectionService {

    private final CodefConnectionClient codefConnectionClient;
    private final CodefConnectionMapper codefConnectionMapper;
    private final BirthDateCipher birthDateCipher;

    public CodefConnection registerAndSave(Long userId, String organizationCode,
                                           String businessType, String clientType,
                                           String loginId, String rawPassword, String birthDate) {
        EncryptedBirthDate encryptedBirthDate = birthDate == null ? null : birthDateCipher.encrypt(birthDate);

        CodefConnection existing = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (existing != null && existing.getConnectedId() != null
                && !existing.getConnectedId().isBlank()) {
            List<String> registeredKeys = InstitutionKeys.parse(existing.getRegisteredInstitutionKeys());
            if (registeredKeys.contains(organizationCode)) {
                codefConnectionClient.updateConnection(
                        existing.getConnectedId(),
                        organizationCode,
                        businessType,
                        clientType,
                        loginId,
                        rawPassword,
                        birthDate
                );
                if (encryptedBirthDate == null) {
                    return existing;
                }
                applyEncryptedBirthDate(existing, encryptedBirthDate);
                codefConnectionMapper.upsert(existing);
                return codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
            }

            codefConnectionClient.addConnection(
                    existing.getConnectedId(),
                    organizationCode,
                    businessType,
                    clientType,
                    loginId,
                    rawPassword,
                    birthDate
            );

            registeredKeys.add(organizationCode);
            existing.setRegisteredInstitutionKeys(InstitutionKeys.serialize(registeredKeys));
            applyEncryptedBirthDate(existing, encryptedBirthDate);
            codefConnectionMapper.upsert(existing);
            return codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        }

        String connectedId = codefConnectionClient.createConnection(
                organizationCode, businessType, clientType, loginId, rawPassword, birthDate
        );

        CodefConnection connection = new CodefConnection();
        connection.setUserId(userId);
        connection.setProvider(ConnectionProvider.CODEF.name());
        connection.setConnectedId(connectedId);
        connection.setRegisteredInstitutionKeys(InstitutionKeys.serialize(List.of(organizationCode)));
        applyEncryptedBirthDate(connection, encryptedBirthDate);
        codefConnectionMapper.upsert(connection);

        CodefConnection saved = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (saved == null || !connectedId.equals(saved.getConnectedId())) {
            throw new IllegalStateException("CODEF connectedId 저장 확인 실패");
        }
        return saved;
    }

    /** encryptedBirthDate가 없으면(이 은행이 birthDate를 요구하지 않으면) 기존 값을 건드리지 않는다. */
    private static void applyEncryptedBirthDate(CodefConnection connection, EncryptedBirthDate encryptedBirthDate) {
        if (encryptedBirthDate == null) {
            return;
        }
        connection.setBirthDateCiphertext(encryptedBirthDate.ciphertextBase64());
        connection.setBirthDateIv(encryptedBirthDate.ivBase64());
        connection.setBirthDateKeyVersion(encryptedBirthDate.keyVersion());
    }
}
