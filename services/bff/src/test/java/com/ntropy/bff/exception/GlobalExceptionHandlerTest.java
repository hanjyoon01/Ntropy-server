package com.ntropy.bff.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.common.ErrorCode;
import com.ntropy.common.exception.ServiceException;

class GlobalExceptionHandlerTest {

    @Test
    void returnsActualHttpStatusMatchingResponseBody() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleServiceException(new ServiceException(ErrorCode.NOT_FOUND));

        assertEquals(404, response.getStatusCodeValue());
        assertEquals(404, response.getBody().getStatus_code());
        assertFalse(response.getBody().isSuccess());
    }
}
