package io.github.gusforge.foundry.support;

/** 成功响应。只有 data，错误走 HTTP 状态码加 ErrorResponse。 */
public record DataResponse<T>(T data) {

    public static <T> DataResponse<T> of(T data) {
        return new DataResponse<>(data);
    }
}
