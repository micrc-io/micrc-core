package io.micrc.core.rpc;

import lombok.Data;

/**
 * 通用返回结果
 *
 * @param <T>
 */
@Data
public class Result<T> {

    /**
     * 返回码
     */
    protected String code = "200";

    /**
     * 返回消息
     */
    protected String message;

    /**
     * 数据列表
     */
    protected T data;

    public Result() {
        this.code = "200";
    }

    public Result(T data) {
        this.code = "200";
        this.data = data;
    }

    public Result(T data, String msg) {
        this.data = data;
        this.code = "200";
        this.message = msg;
    }

    public Result<T> result(ErrorInfo error, T data) {
        Result<T> result = new Result<>();
        if (null != error) {
            result.setCode(error.getErrorCode());
        }
        result.setData(data);
        return result;
    }

    public Result<T> result(T data) {
        Result<T> result = new Result<>();
        result.setCode("200");
        result.setData(data);
        return result;
    }

    public Result(Throwable e) {
        this.message = e.getMessage();
    }
}
