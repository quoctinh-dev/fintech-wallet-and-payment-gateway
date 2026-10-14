package com.fintech.wallet.common.exception;

import com.fintech.wallet.common.dto.ApiError;
import com.fintech.wallet.common.dto.ApiResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ============================================================
    // 1. Business Exception
    // ============================================================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex
    ) {
        ErrorCode errorCode = ex.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    // ============================================================
    // 2. Validation Exception
    // ============================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        List<ApiError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> ApiError.builder()
                        .field(fieldError.getField())
                        .message(fieldError.getDefaultMessage())
                        .build())
                .toList();

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_INPUT,
                        errors
                ));
    }

    // ============================================================
    // 3. JSON Request Body không hợp lệ
    // ============================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex
    ) {
        log.warn(
                "Nội dung request JSON không hợp lệ: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST_BODY.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST_BODY
                ));
    }

    // ============================================================
    // 4. gRPC Exception
    // ============================================================

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleGrpcException(
            StatusRuntimeException ex
    ) {
        String statusCode = ex.getStatus().getCode().name();
        String description = ex.getStatus().getDescription();

        log.error(
                "Gọi gRPC thất bại - mã trạng thái: {}, mô tả: {}",
                statusCode,
                description,
                ex
        );

        if (description != null && !description.isBlank()) {
            try {
                ErrorCode errorCode = ErrorCode.valueOf(description);

                return ResponseEntity
                        .status(errorCode.getHttpStatus())
                        .body(ApiResponse.error(errorCode));

            } catch (IllegalArgumentException ignored) {
                log.warn(
                        "Không tìm thấy ErrorCode tương ứng với lỗi gRPC: {}",
                        description
                );
            }
        }

        /*
         * Nếu không thể chuyển description thành ErrorCode,
         * trả về lỗi hệ thống mặc định.
         */
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
    }

    // ============================================================
    // 5. Access Denied Exception
    // ============================================================

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex
    ) {
        log.warn("Truy cập bị từ chối: {}", ex.getMessage());

        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN)); 
    }

    // ============================================================
    // 6. Unhandled Exception
    // ============================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUncaughtException(
            Exception ex
    ) {
        log.error("Đã xảy ra lỗi không được xử lý", ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
