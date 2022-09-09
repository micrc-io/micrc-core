package io.micrc.core.framework.valobj;

import lombok.Data;
import lombok.ToString;

/**
 * 通用返回结果
 *
 * @param <T>
 */
@Data
@ToString(callSuper = true)
public class Result<T> {

    /**
     * 返回码
     */
    protected int code = 0;

    /**
     * 返回消息
     */
    protected String message;

    /**
     * 数据列表
     */
    protected T data;

    public Result() {
        super();
        this.code = 200;
    }

    public Result(T data) {
        super();
        this.code = 200;
        this.data = data;
    }

    public Result(T data, String msg) {
        super();
        this.data = data;
        this.code = 200;
        this.message = msg;
    }

    public Result(Throwable e) {
        super();
        this.message = e.getMessage();
        this.code = -1;
    }
}
