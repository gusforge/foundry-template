package io.github.gusforge.foundry.exception;

/** 系统异常：基础设施/集成故障，通常可重试。 */
public class SysException extends RuntimeException {

    public SysException(String message) {
        super(message);
    }

    public SysException(String message, Throwable cause) {
        super(message, cause);
    }
}
