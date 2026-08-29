package com.ntropy.bff.dto.common;

import com.ntropy.common.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private int status_code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, 200, "요청에 성공하였습니다.", data);
    }

    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return new ApiResponse<>(true, statusCode, message, data);
    }

    /** 정해진 에러코드 그대로 실패 응답을 만들 때 사용 (매직넘버/하드코딩 문자열 대신). */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getStatusCode(), errorCode.getMessage(), null);
    }

    /** 정해진 에러코드에 상황별 부가 설명을 덧붙이고 싶을 때 사용. */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String detail) {
        return new ApiResponse<>(false, errorCode.getStatusCode(), errorCode.getMessage() + " " + detail, null);
    }


    /** 도메인 서비스가 던진 ServiceException을 그대로 실패 응답으로 변환할 때 사용. */
    public static <T> ApiResponse<T> fail(ServiceException e) {
        return new ApiResponse<>(false, e.getStatusCode(), e.getErrorMessage(), null);
    }
}