package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TxnAnalysisMapperContractTest {

    @Test
    void noLongerDeclaresDuplicateClassificationTargetQuery() throws IOException {
        String mapper = readMapper();

        assertFalse(mapper.contains("findClassificationTargets"));
        assertFalse(mapper.contains("classificationTargetResultMap"));
    }

    @Test
    void keepsOnlyTxnAnalysisSaveStatements() throws IOException {
        String mapper = readMapper();

        assertTrue(mapper.contains("<insert id=\"upsert\""));
        assertTrue(mapper.contains("<insert id=\"upsertAnalyses\""));
        assertTrue(mapper.contains("<insert id=\"upsertDailyAnalyses\""));
        assertTrue(mapper.contains("INSERT INTO TXN_ANALYSIS"));
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/TxnAnalysisMapper.xml";
        try (InputStream input = TxnAnalysisMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
