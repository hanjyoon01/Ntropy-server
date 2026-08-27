package com.ntropy.account.client.codef;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/** CODEF 오류 응답을 민감정보 노출 없이 예외 메시지로 변환한다. */
final class CodefErrorMessage {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Pattern SENSITIVE_NUMBER = Pattern.compile("(?<!\\d)\\d[\\d-]{4,}\\d(?!\\d)");

    private CodefErrorMessage() {
    }

    static String from(JsonNode response) {
        JsonNode result = response.path("result");
        String code = text(result, "code", "CF-UNKNOWN");
        String message = text(result, "message", "알 수 없는 오류");
        String extraMessage = text(result, "extraMessage", null);
        String transactionId = text(result, "transactionId", null);

        String detail = extraMessage == null ? message : message + " - " + extraMessage;
        String formatted = code + " " + sanitize(detail);
        return transactionId == null
                ? formatted
                : formatted + " [transactionId=" + sanitize(transactionId) + "]";
    }

    private static String text(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.isTextual() ? value.asText() : value.toString();
        return text.isBlank() ? fallback : text;
    }

    private static String sanitize(String value) {
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String masked = SENSITIVE_NUMBER.matcher(singleLine).replaceAll("******");
        if (masked.length() <= MAX_MESSAGE_LENGTH) {
            return masked;
        }
        return masked.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }
}
