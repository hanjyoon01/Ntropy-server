package com.ntropy.user.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class UserMapperUserScopeContractTest {

    @Test
    void realOnlyIncludesActiveUsersWhoseProviderIsNull() throws IOException {
        String mapperXml;
        try (var input = getClass().getClassLoader().getResourceAsStream("mapper/UserMapper.xml")) {
            if (input == null) {
                throw new IllegalStateException("mapper/UserMapper.xml을 찾을 수 없습니다");
            }
            mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(
                mapperXml.contains("AND (provider IS NULL OR provider != #{virtualProvider})"),
                "REAL_ONLY는 provider가 NULL인 실제 사용자도 포함해야 합니다"
        );
    }
}
