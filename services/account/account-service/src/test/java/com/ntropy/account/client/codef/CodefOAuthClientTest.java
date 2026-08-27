package com.ntropy.account.client.codef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.config.CodefServiceType;
import com.ntropy.account.domain.entity.CodefToken;
import com.ntropy.account.repository.CodefTokenStore;

class CodefOAuthClientTest {

    @Test
    void issuesAndStoresTokenForSelectedEnvironmentAndClient() {
        CodefProperties properties = properties(CodefServiceType.DEMO);
        InMemoryTokenStore tokenStore = new InMemoryTokenStore();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String basicCredentials = Base64.getEncoder().encodeToString(
                "client-id:client-secret".getBytes(StandardCharsets.UTF_8)
        );
        String response = URLEncoder.encode(
                "{\"access_token\":\"issued-token\",\"token_type\":\"bearer\",\"expires_in\":604799}",
                StandardCharsets.UTF_8
        );

        server.expect(requestTo("https://oauth.codef.io/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basicCredentials))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        CodefOAuthClient client = new CodefOAuthClient(
                properties, tokenStore, restTemplate, new ObjectMapper()
        );

        assertEquals("issued-token", client.getValidAccessToken());
        assertNotNull(tokenStore.token);
        assertEquals("DEMO", tokenStore.token.getServiceType());
        assertEquals("client-id", tokenStore.token.getClientId());
        assertTrue(tokenStore.token.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
        server.verify();
    }

    @Test
    void reusesPersistedTokenWhenItHasMoreThanOneHourLeft() {
        CodefProperties properties = properties(CodefServiceType.SANDBOX);
        InMemoryTokenStore tokenStore = new InMemoryTokenStore();
        tokenStore.token = token("SANDBOX", "client-id", "cached-token", LocalDateTime.now().plusHours(2));

        CodefOAuthClient client = new CodefOAuthClient(
                properties, tokenStore, new RestTemplate(), new ObjectMapper()
        );

        assertEquals("cached-token", client.getValidAccessToken());
        assertEquals(0, tokenStore.saveCount);
    }

    private static CodefProperties properties(CodefServiceType serviceType) {
        return new CodefProperties(
                serviceType.name(), "client-id", "client-secret", "public-key", 5000, 10000
        );
    }

    private static CodefToken token(
            String serviceType, String clientId, String accessToken, LocalDateTime expiresAt
    ) {
        CodefToken token = new CodefToken();
        token.setServiceType(serviceType);
        token.setClientId(clientId);
        token.setAccessToken(accessToken);
        token.setExpiresAt(expiresAt);
        return token;
    }

    private static class InMemoryTokenStore implements CodefTokenStore {
        private CodefToken token;
        private int saveCount;

        @Override
        public void save(CodefToken token) {
            this.token = token;
            saveCount++;
        }

        @Override
        public Optional<CodefToken> findLatest(CodefServiceType serviceType, String clientId) {
            if (token == null || !serviceType.name().equals(token.getServiceType())
                    || !clientId.equals(token.getClientId())) {
                return Optional.empty();
            }
            return Optional.of(token);
        }
    }
}
