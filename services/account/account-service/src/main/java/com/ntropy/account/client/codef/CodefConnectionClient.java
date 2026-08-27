package com.ntropy.account.client.codef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse;
import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse.AccountResult;
import com.ntropy.account.client.codef.support.RsaUtil;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 계정 등록({@code /v1/account/create}) API를 호출해 connectedId를 발급받는다.
 * 환경 선택과 CODEF 고유의 인코딩/디코딩, 토큰 재시도는 {@link CodefApiClient}에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class CodefConnectionClient {

    private static final String ACCOUNT_CREATE_PATH = "/v1/account/create";
    private static final String ACCOUNT_ADD_PATH = "/v1/account/add";
    private static final String ACCOUNT_UPDATE_PATH = "/v1/account/update";

    private final CodefProperties codefProperties;
    private final CodefApiClient codefApiClient;

    /**
     * @param organizationCode CODEF 기관코드 (예: 국민은행 0004)
     * @param businessType     BK(은행/저축은행), CD(카드), ST(증권), IS(보험)
     * @param clientType       P(개인), B(기업/법인), A(통합)
     * @param loginId          대상기관 로그인 아이디
     * @param rawPassword      대상기관 로그인 비밀번호 평문 (이 메서드 안에서 RSA 암호화 후 전송, 평문은 저장하지 않음)
     * @param birthDate        생년월일 (기관에 따라 필요, 없으면 null)
     * @return CODEF가 발급한 connectedId
     */
    public String createConnection(String organizationCode, String businessType, String clientType,
                                    String loginId, String rawPassword, String birthDate) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("accountList", List.of(createAccount(
                    organizationCode, businessType, clientType, loginId, rawPassword, birthDate
            )));

            CodefConnectionCreateResponse response = codefApiClient.post(
                    ACCOUNT_CREATE_PATH,
                    requestBody,
                    CodefConnectionCreateResponse.class
            );

            if (response.getResult() == null || !"CF-00000".equals(response.getResult().getCode())
                    || response.getData() == null || response.getData().getConnectedId() == null
                    || response.getData().getConnectedId().isBlank()) {
                String code = response.getResult() != null ? response.getResult().getCode() : null;
                String message = response.getResult() != null ? response.getResult().getMessage() : "알 수 없는 오류";
                handleAccountFailure("등록", code, message, birthDate);
            }
            validateInstitutionSuccess(response, organizationCode, "등록", birthDate);
            return response.getData().getConnectedId();
        } catch (ServiceException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CODEF 계정 등록 요청 실패", e);
        }
    }

    /**
     * 이미 발급된 connectedId에 다른 기관의 개인 계정을 추가한다.
     */
    public void addConnection(String connectedId, String organizationCode,
                              String businessType, String clientType,
                              String loginId, String rawPassword, String birthDate) {
        changeConnection(
                ACCOUNT_ADD_PATH, "추가", connectedId, organizationCode,
                businessType, clientType, loginId, rawPassword, birthDate
        );
    }

    /**
     * 기존 connectedId에 등록된 기관의 로그인 정보를 갱신한다.
     */
    public void updateConnection(String connectedId, String organizationCode,
                                 String businessType, String clientType,
                                 String loginId, String rawPassword, String birthDate) {
        changeConnection(
                ACCOUNT_UPDATE_PATH, "수정", connectedId, organizationCode,
                businessType, clientType, loginId, rawPassword, birthDate
        );
    }

    private void changeConnection(String path, String action, String connectedId, String organizationCode,
                                  String businessType, String clientType,
                                  String loginId, String rawPassword, String birthDate) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("accountList", List.of(createAccount(
                    organizationCode, businessType, clientType, loginId, rawPassword, birthDate
            )));
            requestBody.put("connectedId", connectedId);

            CodefConnectionCreateResponse response = codefApiClient.post(
                    path,
                    requestBody,
                    CodefConnectionCreateResponse.class
            );
            if (response.getResult() == null || !"CF-00000".equals(response.getResult().getCode())) {
                String code = response.getResult() != null ? response.getResult().getCode() : null;
                String message = response.getResult() != null ? response.getResult().getMessage() : "알 수 없는 오류";
                handleAccountFailure(action, code, message, birthDate);
            }
            validateInstitutionSuccess(response, organizationCode, action, birthDate);
        } catch (ServiceException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CODEF 계정 " + action + " 요청 실패", e);
        }
    }

    private Map<String, Object> createAccount(String organizationCode, String businessType,
                                              String clientType, String loginId,
                                              String rawPassword, String birthDate) throws Exception {
        String encryptedPassword = RsaUtil.encryptWithPublicKey(rawPassword, codefProperties.getPublicKey());

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", businessType);
        account.put("clientType", clientType);
        account.put("organization", organizationCode);
        account.put("loginType", "1");
        account.put("id", loginId);
        account.put("password", encryptedPassword);
        if (birthDate != null) {
            account.put("birthDate", birthDate);
        }
        return account;
    }

    private static void validateInstitutionSuccess(CodefConnectionCreateResponse response,
                                                    String organizationCode, String action, String birthDate) {
        if (response.getData() != null && response.getData().getSuccessList() != null) {
            boolean succeeded = response.getData().getSuccessList().stream()
                    .anyMatch(item -> organizationCode.equals(item.getOrganization())
                            && "CF-00000".equals(item.getCode()));
            if (succeeded) {
                return;
            }
        }

        AccountResult failure = findInstitutionResult(
                response.getData() != null ? response.getData().getErrorList() : null,
                organizationCode
        );
        String code = failure == null ? null : failure.getCode();
        String message = failure == null ? "기관별 성공 결과가 없습니다" : failure.getMessage();
        handleAccountFailure(action, code, message, birthDate);
    }

    /**
     * CODEF는 생년월일 오입력을 별도 결과코드로 명세하지 않는다. 공식 명세·fixture로 정확한
     * 코드가 확인되기 전까지는 실패 메시지에 "생년월일"과 불일치 계열 표현이 함께 있을 때만
     * 오입력으로 최선 추정한다. 유효성 검증 처리 오류처럼 의미가 다른 메시지는 일반 실패로 유지한다.
     * TODO(#98): 실제 CODEF 오입력 응답 코드가 확인되면 키워드 기반 추정 대신 코드 매핑으로 교체한다.
     */
    private static void handleAccountFailure(String action, String code, String message, String birthDate) {
        if (birthDate != null && isBirthDateMismatchMessage(message)) {
            throw new ServiceException(AccountErrorCode.BIRTH_DATE_MISMATCH, "생년월일을 다시 확인해주세요.");
        }
        String detail = (code == null ? "" : code + " ") + (message == null ? "" : message);
        throw new IllegalStateException("CODEF 계정 " + action + " 실패: " + detail.trim());
    }

    private static boolean isBirthDateMismatchMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.replaceAll("\\s", "");
        if (!normalized.contains("생년월일")) {
            return false;
        }
        return normalized.contains("불일치")
                || normalized.contains("일치하지않")
                || normalized.contains("오입력")
                || normalized.contains("다시확인");
    }

    private static AccountResult findInstitutionResult(List<AccountResult> results, String organizationCode) {
        if (results == null) {
            return null;
        }
        return results.stream()
                .filter(item -> organizationCode.equals(item.getOrganization()))
                .findFirst()
                .orElse(null);
    }

}
