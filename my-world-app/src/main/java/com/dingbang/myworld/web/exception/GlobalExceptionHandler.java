package com.dingbang.myworld.web.exception;

import com.dingbang.myworld.common.api.ApiResponse;
import com.dingbang.myworld.common.exception.BusinessException;
import com.dingbang.myworld.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author dingbang.tian
 * @since 2026/09/21
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 日志记录器
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     *
     * @param exception
     * @return responseEntity
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        LOGGER.warn("Business exception, code={}, message={}", errorCode.getCode(), exception.getMessage());
        return ResponseEntity.status(resolveHttpStatus(errorCode))
                .body(ApiResponse.failure(errorCode.getCode(), exception.getMessage()));
    }

    /**
     * 处理请求体参数校验异常
     *
     * @param exception
     * @return responseEntity
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return buildBadRequest(message);
    }

    /**
     * 处理约束校验异常
     *
     * @param exception
     * @return responseEntity
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        return buildBadRequest(exception.getMessage());
    }

    /**
     * 兜底处理未预期异常
     *
     * @param exception
     * @return responseEntity
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        LOGGER.error("Unexpected system exception", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(resolveHttpStatus(errorCode))
                .body(ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 构造参数错误响应
     *
     * @param message
     * @return responseEntity
     */
    private ResponseEntity<ApiResponse<Void>> buildBadRequest(String message) {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;
        LOGGER.warn("Request validation failed, message={}", message);
        return ResponseEntity.status(resolveHttpStatus(errorCode))
                .body(ApiResponse.failure(errorCode.getCode(), message));
    }

    /**
     * 将通用错误码映射为HTTP状态
     *
     * @param errorCode
     * @return httpStatus
     */
    private HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
