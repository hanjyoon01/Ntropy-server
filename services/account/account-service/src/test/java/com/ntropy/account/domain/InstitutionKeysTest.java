package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class InstitutionKeysTest {

    @Test
    void parsesBlankOrNullAsEmptyList() {
        assertTrue(InstitutionKeys.parse(null).isEmpty());
        assertTrue(InstitutionKeys.parse("").isEmpty());
        assertTrue(InstitutionKeys.parse("   ").isEmpty());
    }

    @Test
    void roundTripsThroughSerializeAndParse() {
        List<String> keys = List.of("0004", "0088");

        String json = InstitutionKeys.serialize(keys);
        List<String> parsed = InstitutionKeys.parse(json);

        assertEquals(keys, parsed);
    }

    @Test
    void parsedListIsMutable() {
        List<String> parsed = InstitutionKeys.parse("[\"0004\"]");
        parsed.add("0088");

        assertEquals(List.of("0004", "0088"), parsed);
    }
}
