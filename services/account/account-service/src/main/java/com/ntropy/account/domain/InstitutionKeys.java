package com.ntropy.account.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code CODEF_CONNECTION.registered_institution_keys}에 저장되는 JSON 배열 원문과
 * {@code List<String>} 사이의 변환을 담당한다.
 */
public final class InstitutionKeys {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InstitutionKeys() {
    }

    public static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            throw new IllegalStateException("등록기관 목록 파싱 실패: " + json, e);
        }
    }

    public static String serialize(List<String> keys) {
        try {
            return OBJECT_MAPPER.writeValueAsString(keys);
        } catch (Exception e) {
            throw new IllegalStateException("등록기관 목록 직렬화 실패", e);
        }
    }
}
