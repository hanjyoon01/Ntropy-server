package com.ntropy.bff.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.ntropy.common.exception.ServiceException;

class AuthenticatedUserIdResolverTest {

    private final AuthenticatedUserIdResolver resolver = new AuthenticatedUserIdResolver();

    @Test
    void extractsNumericUserIdFromAuthenticatedPrincipal() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("42", null, List.of());

        assertEquals(42L, resolver.resolve(authentication));
    }

    @Test
    void rejectsMissingOrNonNumericPrincipal() {
        assertEquals(401, assertThrows(ServiceException.class, () -> resolver.resolve(null)).getStatusCode());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("member@example.com", null, List.of());
        assertEquals(
                401,
                assertThrows(ServiceException.class, () -> resolver.resolve(authentication)).getStatusCode()
        );
    }
}
