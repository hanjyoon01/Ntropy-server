package com.ntropy.bff.controller.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.bff.exception.GlobalExceptionHandler;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.user.api.client.UserCommandClient;
import com.ntropy.user.api.dto.OAuthLoginResult;
import com.ntropy.user.api.dto.TokenPair;
import com.ntropy.user.api.dto.UserUpdateCommand;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.api.client.UserQueryClient;
import com.ntropy.user.api.dto.UserSummary;

class AuthControllerContractTest {

    private StubUserCommandClient userCommandClient;
    private StubUserQueryClient userQueryClient;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userCommandClient = new StubUserCommandClient();
        userQueryClient = new StubUserQueryClient();
        objectMapper = new ObjectMapper();
        AuthController controller = new AuthController(
                userCommandClient, userQueryClient, new AuthenticatedUserIdResolver()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void virtualLoginUsesJsonUserNumberAndReturnsOAuthResponseContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/virtual-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"virtualUserNumber\":7}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
        );
        assertEquals("virtual-access", body.path("data").path("accessToken").asText());
        assertEquals("virtual-refresh", body.path("data").path("refreshToken").asText());
        assertEquals(7007L, body.path("data").path("userId").asLong());

        assertEquals(7, userCommandClient.virtualUserNumber);
    }

    @Test
    void virtualLoginRejectsMissingUserNumber() throws Exception {
        mockMvc.perform(post("/api/auth/virtual-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void virtualLoginPropagatesNotFoundWhenDisabledOrNotSeeded() throws Exception {
        userCommandClient.virtualLoginError = ErrorCode.NOT_FOUND;

        mockMvc.perform(post("/api/auth/virtual-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"virtualUserNumber\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void meReturnsAuthenticatedUserSummary() throws Exception {
        userQueryClient.summary = new UserSummary(
                7007L, "가상회원7", "virtual-user-000007@ntropy.test", "NTROPY_TEST", false, false, true
        );

        MvcResult result = mockMvc.perform(get("/api/auth/me").principal(authentication(7007L)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertEquals(7007L, data.path("userId").asLong());
        assertEquals("NTROPY_TEST", data.path("provider").asText());
        assertEquals(7007L, userQueryClient.requestedUserId);
    }

    @Test
    void meReturnsNotFoundWhenUserSummaryMissing() throws Exception {
        userQueryClient.summary = null;

        mockMvc.perform(get("/api/auth/me").principal(authentication(9999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshReturnsRotatedTokenPair() throws Exception {
        userCommandClient.refreshResult = new TokenPair("rotated-access", "rotated-refresh");

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertEquals("rotated-access", data.path("accessToken").asText());
        assertEquals("rotated-refresh", data.path("refreshToken").asText());
        assertEquals("old-refresh", userCommandClient.refreshedToken);
    }

    @Test
    void logoutInvalidatesRefreshTokenForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/logout").principal(authentication(7007L)))
                .andExpect(status().isOk());

        assertEquals(7007L, userCommandClient.loggedOutUserId);
    }

    private static Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, "unused", Collections.emptyList());
    }

    private static final class StubUserQueryClient implements UserQueryClient {

        private UserSummary summary;
        private Long requestedUserId;

        @Override
        public UserSummary getUserSummary(Long userId) {
            requestedUserId = userId;
            return summary;
        }
    }

    private static final class StubUserCommandClient implements UserCommandClient {

        private int virtualUserNumber;
        private ErrorCode virtualLoginError;
        private TokenPair refreshResult;
        private String refreshedToken;
        private Long loggedOutUserId;

        @Override
        public OAuthLoginResult loginWithOAuthCode(String provider, String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OAuthLoginResult loginAsVirtualUser(int virtualUserNumber) {
            this.virtualUserNumber = virtualUserNumber;
            if (virtualLoginError != null) {
                throw new ServiceException(virtualLoginError);
            }
            return new OAuthLoginResult(
                    "virtual-access", "virtual-refresh", 7007L,
                    "가상회원7", "virtual-user-000007@ntropy.test", true
            );
        }

        @Override
        public TokenPair refreshAccessToken(String refreshToken) {
            refreshedToken = refreshToken;
            return refreshResult;
        }

        @Override
        public void logout(Long userId) {
            loggedOutUserId = userId;
        }

        @Override
        public void updateUser(Long userId, UserUpdateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteUser(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeOnboarding(Long userId) {
            throw new UnsupportedOperationException();
        }
    }
}
