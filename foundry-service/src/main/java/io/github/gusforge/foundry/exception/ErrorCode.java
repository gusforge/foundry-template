package io.github.gusforge.foundry.exception;

import lombok.Getter;

/**
 * 错误码注册表。号段约定：1xxxxx 通用，3xxxxx 业务域。
 *
 * <p>httpStatus 用 int 而不是 Spring 的 HttpStatus，避免 service 模块依赖 spring-web。
 */
@Getter
public enum ErrorCode {
    INVALID_PARAM(100001, "参数不合法", 400),
    NOT_FOUND(100002, "资源不存在", 404),

    DEMO_NOT_FOUND(300001, "示例数据不存在", 404),
    DEMO_NAME_DUPLICATED(300002, "示例名称已存在", 409);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
