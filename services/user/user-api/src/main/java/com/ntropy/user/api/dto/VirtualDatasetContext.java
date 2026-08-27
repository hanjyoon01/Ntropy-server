package com.ntropy.user.api.dto;

import java.time.LocalDate;

/**
 * 가상 데이터 생성기가 결정적으로 재현하기 위해 공유해야 하는 실행 컨텍스트.
 * 각 도메인 생성기는 이 값을 그대로 받아 쓰고, 자체적으로 {@code LocalDate.now()}나 별도 seed를 읽지 않는다.
 */
public record VirtualDatasetContext(
        String datasetVersion,
        LocalDate referenceDate,
        long randomSeed
) {
}
