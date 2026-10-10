package io.github.gusforge.foundry.support;

import java.time.Instant;
import org.slf4j.MDC;

/**
 * 错误响应。
 *
 * @param code 业务错误码；未预期的系统异常为 0
 * @param traceId 与日志、响应头 X-Trace-Id 一致，调用方凭它找排查线索
 */
public record ErrorResponse(int code, String message, String traceId, Instant timestamp) {

    public static ErrorResponse of(int code, String message) {
        return new ErrorResponse(code, message, MDC.get(TraceIdFilter.TRACE_ID), Instant.now());
    }
}
