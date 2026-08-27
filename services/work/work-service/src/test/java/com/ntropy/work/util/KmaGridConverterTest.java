package com.ntropy.work.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KmaGridConverterTest {

    @Test
    @DisplayName("서울시청 좌표는 기상청 공식 격자(60,127)로 변환된다")
    void toGrid_seoulCityHall_matchesOfficialGrid() {
        KmaGridConverter.Grid grid = KmaGridConverter.toGrid(37.5665, 126.9780);

        assertEquals(60, grid.nx());
        assertEquals(127, grid.ny());
    }
}
