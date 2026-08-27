package com.ntropy.account.service;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.client.codef.CodefBankAccountClient;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 지원 은행의 CODEF 개인 ID 로그인 연결과 보유계좌 조회를 조합한다.
 * 로그인 비밀번호와 생년월일은 요청 시점에만 사용하고 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PersonalBankAccountService {

    private static final String BUSINESS_TYPE = "BK";
    private static final String CLIENT_TYPE = "P";

    private final CodefConnectionService codefConnectionService;
    private final CodefConnectionMapper codefConnectionMapper;
    private final CodefBankAccountClient codefBankAccountClient;

    public CodefConnection registerPersonalAccount(Long userId, PersonalBank bank,
                                                   String loginId, String rawPassword,
                                                   String birthDate) {
        validateUserId(userId);
        requireBank(bank);
        requireNonBlank(loginId, bank.getDisplayName() + " 로그인 ID");
        requireNonBlank(rawPassword, bank.getDisplayName() + " 로그인 비밀번호");

        String normalizedBirthDate = bank.normalizeBirthDate(birthDate);
        return codefConnectionService.registerAndSave(
                userId,
                bank.getOrganizationCode(),
                BUSINESS_TYPE,
                CLIENT_TYPE,
                loginId,
                rawPassword,
                normalizedBirthDate
        );
    }

    public JsonNode getPersonalAccountList(Long userId, PersonalBank bank) {
        validateUserId(userId);
        requireBank(bank);

        CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (connection == null || connection.getConnectedId() == null
                || connection.getConnectedId().isBlank()) {
            throw new IllegalStateException("등록된 CODEF 연결이 없습니다");
        }

        return codefBankAccountClient.getPersonalAccountList(
                bank.getOrganizationCode(),
                connection.getConnectedId()
        );
    }

    public JsonNode registerAndGetPersonalAccountList(Long userId, PersonalBank bank,
                                                     String loginId, String rawPassword,
                                                     String birthDate) {
        registerPersonalAccount(userId, bank, loginId, rawPassword, birthDate);
        return getPersonalAccountList(userId, bank);
    }

    private static void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다");
        }
    }

    private static void requireBank(PersonalBank bank) {
        if (bank == null) {
            throw new IllegalArgumentException("은행이 필요합니다");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "가 필요합니다");
        }
    }
}
