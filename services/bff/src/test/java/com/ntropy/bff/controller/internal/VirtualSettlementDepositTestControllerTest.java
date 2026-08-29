package com.ntropy.bff.controller.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient;
import com.ntropy.work.api.client.VirtualSettlementDepositBatchCommandClient.BatchResult;

class VirtualSettlementDepositTestControllerTest {

    @Test
    void exposesInternalTestEndpoint() throws Exception {
        RequestMapping requestMapping = VirtualSettlementDepositTestController.class
                .getAnnotation(RequestMapping.class);
        Method run = VirtualSettlementDepositTestController.class
                .getDeclaredMethod("run", Long.class, LocalDate.class);

        assertArrayEquals(new String[] {"/internal/test/virtual-settlement"}, requestMapping.value());
        assertArrayEquals(new String[] {"/run"}, run.getAnnotation(GetMapping.class).value());
    }

    @Test
    void returnsCreatedDepositAndMatchedSettlementCounts() {
        RecordingBatchClient client = new RecordingBatchClient(new BatchResult(2, 3));
        VirtualSettlementDepositTestController controller =
                new VirtualSettlementDepositTestController(client);
        LocalDate date = LocalDate.of(2026, 8, 23);

        ApiResponse<BatchResult> response = controller.run(42L, date);

        assertEquals(42L, client.userId);
        assertEquals(date, client.date);
        assertEquals(200, response.getStatus_code());
        assertEquals(2, response.getData().createdDepositCount());
        assertEquals(3, response.getData().matchedSettlementCount());
        assertEquals(
                "가상 정산 입금 배치를 실행했습니다. 생성 입금 2건, 매칭 SETTLEMENT 3건",
                response.getMessage());
    }

    private static final class RecordingBatchClient implements VirtualSettlementDepositBatchCommandClient {
        private final BatchResult result;
        private Long userId;
        private LocalDate date;

        private RecordingBatchClient(BatchResult result) {
            this.result = result;
        }

        @Override
        public BatchResult runForDate(Long userId, LocalDate processDate) {
            this.userId = userId;
            date = processDate;
            return result;
        }
    }
}
