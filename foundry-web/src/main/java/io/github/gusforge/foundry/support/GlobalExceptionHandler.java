package io.github.gusforge.foundry.support;

import io.github.gusforge.foundry.exception.BizException;
import io.github.gusforge.foundry.exception.ErrorCode;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 继承 ResponseEntityExceptionHandler：Spring MVC 自己的异常（404、405、415、缺参数等） 由它按正确的状态码处理，这里只统一响应体格式。
 *
 * <p>不要直接写一个兜底 {@code @ExceptionHandler(Exception.class)} 而不继承它， 否则 NoResourceFoundException
 * 等框架异常会被吞成 500。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorResponse> onBiz(BizException e) {
        ErrorCode ec = e.getErrorCode();
        // 业务异常是预期内的：warn 级别，不打全栈
        log.warn("biz error: code={} msg={}", ec.getCode(), e.getMessage());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.of(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onUnexpected(Exception e) {
        // 系统异常必须打全栈
        log.error("unexpected error", e);
        // 内部细节（堆栈、SQL、内网地址）不外泄，只给 traceId
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(0, "服务内部错误，请提供 traceId 联系支持"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail =
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + ": " + f.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ErrorCode.INVALID_PARAM.getCode(), detail));
    }

    /** 其余框架异常的统一出口：保留框架给出的状态码，响应体换成我们的格式。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ErrorCode ec =
                status.value() == HttpStatus.NOT_FOUND.value()
                        ? ErrorCode.NOT_FOUND
                        : ErrorCode.INVALID_PARAM;
        String reason =
                status instanceof HttpStatus hs
                        ? hs.getReasonPhrase()
                        : String.valueOf(status.value());
        return ResponseEntity.status(status)
                .headers(headers)
                .body(ErrorResponse.of(ec.getCode(), reason));
    }
}
