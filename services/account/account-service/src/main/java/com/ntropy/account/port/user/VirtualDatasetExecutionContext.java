package com.ntropy.account.port.user;

import java.time.LocalDate;

/**
 * account-service가 가상 금융 데이터를 결정적으로 재현하기 위해 필요로 하는 실행 컨텍스트.
 * user-service의 VirtualDatasetContext와 필드 구성은 같지만, account가 소유한 별개의 타입이다.
 */
public record VirtualDatasetExecutionContext(
        String datasetVersion,
        LocalDate referenceDate,
        long randomSeed
) {
}
