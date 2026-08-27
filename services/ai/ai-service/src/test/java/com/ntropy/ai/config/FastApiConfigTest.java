package com.ntropy.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.ntropy.ai.client.fastapi.FastApiProductRecommendationClient;
import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;

class FastApiConfigTest {

    @Test
    void loadsBaseUrlFromEnvironmentVariableForBothFastApiClients() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            String expectedBaseUrl = "https://fastapi.example.test";

            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "fastApiEnvironment",
                            Map.of("FASTAPI_BASE_URL", expectedBaseUrl)
                    )
            );
            context.register(
                    FastApiProperties.class,
                    FastApiProductRecommendationClient.class,
                    FastApiTransactionClassificationClient.class
            );
            context.refresh();

            assertEquals(
                    expectedBaseUrl,
                    getField(
                            context.getBean(FastApiProductRecommendationClient.class),
                            "fastApiBaseUrl"
                    )
            );
            assertEquals(
                    expectedBaseUrl,
                    getField(
                            context.getBean(FastApiTransactionClassificationClient.class),
                            "fastApiBaseUrl"
                    )
            );
        }
    }

    @Test
    void propertyValueTakesPriorityOverEnvironmentVariable() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        "fastApiSettings",
                        Map.of(
                                "fastapi.base-url", "https://properties.example.test",
                                "FASTAPI_BASE_URL", "https://environment.example.test"
                        )
                )
        );

        FastApiProperties properties = new FastApiProperties(environment);

        assertEquals("https://properties.example.test", properties.getBaseUrl());
    }

    @Test
    void loadsClassificationHttpTimeoutsWithSafeDefaults() {
        StandardEnvironment defaultsEnvironment = new StandardEnvironment();
        defaultsEnvironment.getPropertySources().addFirst(
                new MapPropertySource(
                        "fastApiDefaults",
                        Map.of("fastapi.base-url", "https://fastapi.example.test")
                )
        );

        FastApiProperties defaults = new FastApiProperties(defaultsEnvironment);
        assertEquals(5_000, defaults.getConnectTimeoutMillis());
        assertEquals(120_000, defaults.getReadTimeoutMillis());

        StandardEnvironment configuredEnvironment = new StandardEnvironment();
        configuredEnvironment.getPropertySources().addFirst(
                new MapPropertySource(
                        "fastApiTimeouts",
                        Map.of(
                                "fastapi.base-url", "https://fastapi.example.test",
                                "fastapi.connect-timeout-ms", "3000",
                                "fastapi.read-timeout-ms", "60000"
                        )
                )
        );

        FastApiProperties configured = new FastApiProperties(configuredEnvironment);
        assertEquals(3_000, configured.getConnectTimeoutMillis());
        assertEquals(60_000, configured.getReadTimeoutMillis());
    }

    @Test
    void missingPropertyAndEnvironmentVariableFailsClearly() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME
        );
        environment.getPropertySources().remove(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new FastApiProperties(environment)
        );

        assertEquals(
                "FastAPI 주소가 설정되지 않았습니다. "
                        + "fastapi.base-url 또는 FASTAPI_BASE_URL을 설정하세요.",
                exception.getMessage()
        );
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
