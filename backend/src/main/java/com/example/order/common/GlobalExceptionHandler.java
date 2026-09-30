package com.example.order.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;

import java.util.stream.Collectors;

/**
 * 统一异常处理：所有接口返回格式统一为 {code, message, data}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 主动业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (msg.isEmpty()) msg = "参数校验失败";
        return Result.error(400, msg);
    }

    /** 通用异常兜底 */
    @ExceptionHandler(Exception.class)
    public Result<?> handleGeneral(Exception e) {
        e.printStackTrace();
        return Result.error(500, "服务器异常，请稍后重试");
    }

    /** 主动业务异常 */
    public static class BusinessException extends RuntimeException {
        private final int code;
        public BusinessException(String message) { this(500, message); }
        public BusinessException(int code, String message) { super(message); this.code = code; }
        public int getCode() { return code; }
    }
}
