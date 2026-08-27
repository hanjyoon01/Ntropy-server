package com.ntropy.account.client.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse;
import com.ntropy.account.client.codef.dto.CodefConnectionCreateResponse.AccountResult;
import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.common.exception.ServiceException;

class CodefConnectionClientTest {

    @Test
    void addsAccountToExistingConnectedId() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(successResponse("0004"));
        CodefConnectionClient client = new CodefConnectionClient(properties(), apiClient);

        client.addConnection(
                "connected-id", "0004", "BK", "P",
                "login-id", "raw-password", "19900101"
        );

        assertEquals("/v1/account/add", apiClient.path);
        assertEquals("connected-id", apiClient.requestBody.get("connectedId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> account = ((List<Map<String, Object>>) apiClient.requestBody.get("accountList")).get(0);
        assertEquals("0004", account.get("organization"));
        assertEquals("1", account.get("loginType"));
        assertEquals("login-id", account.get("id"));
        assertEquals("19900101", account.get("birthDate"));
        assertNotEquals("raw-password", account.get("password"));
    }

    @Test
    void updatesAccountCredentialsForExistingInstitution() throws Exception {
        StubCodefApiClient apiClient = new StubCodefApiClient(successResponse("0011"));
        CodefConnectionClient client = new CodefConnectionClient(properties(), apiClient);

        client.updateConnection(
                "connected-id", "0011", "BK", "P",
                "login-id", "raw-password", null
        );

        assertEquals("/v1/account/update", apiClient.path);
        assertEquals("connected-id", apiClient.requestBody.get("connectedId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> account = ((List<Map<String, Object>>) apiClient.requestBody.get("accountList")).get(0);
        assertEquals("0011", account.get("organization"));
        assertEquals("login-id", account.get("id"));
        assertNotEquals("raw-password", account.get("password"));
    }

    @Test
    void rejectsCodefAccountAddFailure() throws Exception {
        CodefConnectionCreateResponse response = new CodefConnectionCreateResponse();
        CodefConnectionCreateResponse.Result result = new CodefConnectionCreateResponse.Result();
        result.setCode("CF-99999");
        result.setMessage("실패");
        response.setResult(result);
        CodefConnectionClient client = new CodefConnectionClient(
                properties(), new StubCodefApiClient(response)
        );

        assertThrows(
                IllegalStateException.class,
                () -> client.addConnection(
                        "connected-id", "0088", "BK", "P", "id", "password", null
                )
        );
    }

    @Test
    void rejectsInstitutionFailureEvenWhenTopLevelResultSucceeds() throws Exception {
        CodefConnectionCreateResponse response = successResponseWithoutAccountResult();
        AccountResult failure = new AccountResult();
        failure.setCode("CF-01001");
        failure.setMessage("아이디 또는 비밀번호 오류");
        failure.setOrganization("0088");
        response.getData().setErrorList(List.of(failure));

        CodefConnectionClient client = new CodefConnectionClient(
                properties(), new StubCodefApiClient(response)
        );

        assertThrows(
                IllegalStateException.class,
                () -> client.addConnection(
                        "connected-id", "0088", "BK", "P", "id", "password", null
                )
        );
    }

    @Test
    void mapsBirthDateMismatchWhenBirthDateSuppliedAndCodefRejectsIt() throws Exception {
        CodefConnectionCreateResponse response = new CodefConnectionCreateResponse();
        CodefConnectionCreateResponse.Result result = new CodefConnectionCreateResponse.Result();
        result.setCode("CF-03002");
        result.setMessage("입력하신 생년월일 정보가 일치하지 않습니다");
        response.setResult(result);
        CodefConnectionClient client = new CodefConnectionClient(
                properties(), new StubCodefApiClient(response)
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.addConnection(
                        "connected-id", "0004", "BK", "P", "id", "password", "19900101"
                )
        );

        assertEquals(AccountErrorCode.BIRTH_DATE_MISMATCH.getStatusCode(), exception.getStatusCode());
        assertFalse(exception.getErrorMessage().contains("CF-03002"), "CODEF 원문 응답코드를 그대로 노출하지 않는다");
        assertFalse(exception.getErrorMessage().contains("19900101"), "입력한 생년월일 값을 노출하지 않는다");
    }

    @Test
    void keepsGenericFailureWhenNoBirthDateWasSupplied() throws Exception {
        CodefConnectionCreateResponse response = new CodefConnectionCreateResponse();
        CodefConnectionCreateResponse.Result result = new CodefConnectionCreateResponse.Result();
        result.setCode("CF-03002");
        result.setMessage("입력하신 생년월일 정보가 일치하지 않습니다");
        response.setResult(result);
        CodefConnectionClient client = new CodefConnectionClient(
                properties(), new StubCodefApiClient(response)
        );

        assertThrows(
                IllegalStateException.class,
                () -> client.addConnection(
                        "connected-id", "0088", "BK", "P", "id", "password", null
                )
        );
    }

    @Test
    void keepsGenericFailureForBirthDateValidationProcessingError() throws Exception {
        CodefConnectionCreateResponse response = new CodefConnectionCreateResponse();
        CodefConnectionCreateResponse.Result result = new CodefConnectionCreateResponse.Result();
        result.setCode("CF-03002");
        result.setMessage("생년월일 유효성 검증 중 오류가 발생했습니다");
        response.setResult(result);
        CodefConnectionClient client = new CodefConnectionClient(
                properties(), new StubCodefApiClient(response)
        );

        assertThrows(
                IllegalStateException.class,
                () -> client.addConnection(
                        "connected-id", "0004", "BK", "P", "id", "password", "19900101"
                )
        );
    }

    private static CodefProperties properties() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return new CodefProperties("DEMO", "client-id", "client-secret", publicKey, 1000, 1000);
    }

    private static CodefConnectionCreateResponse successResponse(String organizationCode) {
        CodefConnectionCreateResponse response = successResponseWithoutAccountResult();
        AccountResult success = new AccountResult();
        success.setCode("CF-00000");
        success.setMessage("정상");
        success.setOrganization(organizationCode);
        response.getData().setSuccessList(List.of(success));
        return response;
    }

    private static CodefConnectionCreateResponse successResponseWithoutAccountResult() {
        CodefConnectionCreateResponse response = new CodefConnectionCreateResponse();
        CodefConnectionCreateResponse.Result result = new CodefConnectionCreateResponse.Result();
        result.setCode("CF-00000");
        result.setMessage("정상");
        response.setResult(result);
        CodefConnectionCreateResponse.Data data = new CodefConnectionCreateResponse.Data();
        data.setSuccessList(List.of());
        data.setErrorList(List.of());
        response.setData(data);
        return response;
    }

    private static class StubCodefApiClient extends CodefApiClient {

        private final CodefConnectionCreateResponse response;
        private String path;
        private Map<String, Object> requestBody;

        StubCodefApiClient(CodefConnectionCreateResponse response) {
            super(null, null, null, null);
            this.response = response;
        }

        @Override
        public <T> T post(String path, Map<String, Object> requestBody, Class<T> responseType) {
            this.path = path;
            this.requestBody = requestBody;
            return responseType.cast(response);
        }
    }
}
