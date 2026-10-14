package com.fintech.wallet.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fintech.wallet.common.exception.ErrorCode;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private Integer code;
    private String message;
    private T data;
    private List<ApiError> errors;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Thao tác thành công", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, List<ApiError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}