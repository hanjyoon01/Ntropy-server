package com.ntropy.bff.exception;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.exception.ServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.ntropy.bff.dto.common.ErrorCode;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceException(ServiceException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.fail(e));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST));
    }
}
