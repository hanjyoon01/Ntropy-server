package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CodefConnectionMapperContractTest {

    @Test
    void bulkLookupKeepsSingleLookupProjectionAndUsesUserIdCollection() throws IOException {
        String mapper = readResource("mapper/account/CodefConnectionMapper.xml");
        String single = statement(mapper, "findByUserIdAndProvider");
        String bulk = statement(mapper, "findByUserIdsAndProvider");

        assertEquals(selectProjection(single), selectProjection(bulk),
                "단건/bulk 조회가 반환하는 CodefConnection 필드는 같아야 합니다");
        assertTrue(bulk.contains("provider = #{provider}"));
        assertTrue(bulk.contains("user_id IN"));
        assertTrue(bulk.contains("<foreach collection=\"userIds\" item=\"userId\""));
    }

    private static String statement(String mapper, String id) {
        String startToken = "<select id=\"" + id + "\"";
        int start = mapper.indexOf(startToken);
        assertTrue(start >= 0, id + " statement가 필요합니다");
        int bodyStart = mapper.indexOf('>', start) + 1;
        int end = mapper.indexOf("</select>", bodyStart);
        assertTrue(end > bodyStart, id + " statement가 닫혀 있어야 합니다");
        return mapper.substring(bodyStart, end);
    }

    private static String selectProjection(String statement) {
        int start = statement.indexOf("SELECT") + "SELECT".length();
        int end = statement.indexOf("FROM CODEF_CONNECTION", start);
        return statement.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = CodefConnectionMapperContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + "가 테스트 classpath에 있어야 합니다");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
