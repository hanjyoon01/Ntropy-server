package com.ntropy.defense.service;

import com.ntropy.defense.domain.DefenseCause;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DefenseChecklistCatalogTest {
    @Test
    void everyCauseHasChecklistItems() {
        Arrays.stream(DefenseCause.values())
                .forEach(cause -> assertFalse(DefenseChecklistCatalog.findBy(cause).isEmpty(), cause.name()));
    }
}
