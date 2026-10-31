package com.ems.admin.common.exception;

import com.ems.admin.common.api.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> biz(BizException e) {
        int code = e.getCode();
        int http = code >= 400 && code < 600 ? code : 400;
        return ResponseEntity.status(http).body(R.fail(code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> valid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + (err.getDefaultMessage() == null ? "不合法" : err.getDefaultMessage()))
                .findFirst()
                .orElse("参数不合法");
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> constraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(R.fail(400, e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(R.fail(403, "没有权限"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(R.fail(405, "请求方法不支持"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> other(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof AccessDeniedException accessDenied) {
                return denied(accessDenied);
            }
            current = current.getCause();
        }
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError().body(R.fail(500, "服务器内部错误"));
    }
}
