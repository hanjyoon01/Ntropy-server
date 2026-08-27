package com.ntropy.account.client.codef.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CODEF 응답은 옵션/미제공 필드가 빈 문자열로 오고, 금액은 문자열·숫자가 섞여 있을 수 있다.
 * 파서들이 공통으로 쓰는 방어적 변환 헬퍼.
 */
final class CodefJsonSupport {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    private CodefJsonSupport() {
    }

    static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    static BigDecimal amount(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        return new BigDecimal(value.replace(",", "").trim());
    }

    static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value, YYYYMMDD);
    }

    static LocalTime time(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.length() != 6) {
            return null;
        }
        return LocalTime.parse(value, HHMMSS);
    }

    static Boolean flag01(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        if ("1".equals(value)) {
            return Boolean.TRUE;
        }
        if ("0".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    static List<JsonNode> accountResults(JsonNode data) {
        if (data.isArray()) {
            List<JsonNode> nodes = new ArrayList<>();
            data.forEach(nodes::add);
            return nodes;
        }
        if (data.isObject()) {
            return List.of(data);
        }
        return List.of();
    }
}
