package com.ntropy.bff.controller.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ntropy.account.api.dto.AccountRegistrationCommand;
import com.ntropy.bff.dto.account.request.AccountRegistrationRequest;

class FinancialAccountControllerContractTest {

    @Test
    void exposesAgreedFinancialApiPaths() throws Exception {
        assertArrayEquals(new String[] {"/api/banks"}, method("getBanks").getAnnotation(GetMapping.class).value());
        assertArrayEquals(
                new String[] {"/api/mydata/status"},
                method("getMyDataStatus", org.springframework.security.core.Authentication.class)
                        .getAnnotation(GetMapping.class).value()
        );
        assertArrayEquals(
                new String[] {"/api/accounts"},
                method("registerAccount", org.springframework.security.core.Authentication.class,
                        AccountRegistrationRequest.class).getAnnotation(PostMapping.class).value()
        );
        assertArrayEquals(
                new String[] {"/api/accounts/{accountId}/deactivate"},
                method("deactivateAccount", org.springframework.security.core.Authentication.class, Long.class)
                        .getAnnotation(PatchMapping.class).value()
        );
        assertArrayEquals(
                new String[] {"/api/accounts/{accountId}/activate"},
                method("activateAccount", org.springframework.security.core.Authentication.class, Long.class)
                        .getAnnotation(PatchMapping.class).value()
        );
    }

    @Test
    void doesNotAcceptUserIdAsRequestParameter() {
        for (Method method : FinancialAccountController.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    assertFalse("userId".equals(requestParam.name()) || "userId".equals(requestParam.value()));
                }
            }
        }
    }

    @Test
    void virtualRegistrationDoesNotRequireBankCredentials() {
        AccountRegistrationRequest request = new AccountRegistrationRequest();
        request.setConnectionType("VIRTUAL");
        request.setOrganizationCode("0088");

        AccountRegistrationCommand command = request.toCommand();

        assertEquals("VIRTUAL", command.connectionType());
        assertEquals("0088", command.organizationCode());
        assertEquals(null, command.bankLoginId());
        assertEquals(null, command.bankLoginPassword());
    }

    @Test
    void codefRegistrationForwardsBirthDateToCommand() {
        AccountRegistrationRequest request = new AccountRegistrationRequest();
        request.setConnectionType("CODEF");
        request.setOrganizationCode("0004");
        request.setBankLoginId("bank-user-id");
        request.setBankLoginPassword("bank-password");
        request.setBirthDate("19900101");

        AccountRegistrationCommand command = request.toCommand();

        assertEquals("CODEF", command.connectionType());
        assertEquals("0004", command.organizationCode());
        assertEquals("bank-user-id", command.bankLoginId());
        assertEquals("bank-password", command.bankLoginPassword());
        assertEquals("19900101", command.birthDate());
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return FinancialAccountController.class.getDeclaredMethod(name, parameterTypes);
    }
}
