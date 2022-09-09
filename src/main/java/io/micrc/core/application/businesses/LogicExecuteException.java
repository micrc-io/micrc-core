package io.micrc.core.application.businesses;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 逻辑执行异常
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/8/29 16:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogicExecuteException extends RuntimeException {
    /**
     * 检查错误码
     */
    private String errorCode = "999999999";

    /**
     * 检查错误信息
     */
    private String errorMessage;

    public LogicExecuteException(Throwable cause) {
        super(cause);
        this.errorMessage = cause.getMessage();
    }

    public LogicExecuteException(String message) {
        super(message);
        this.errorMessage = message;
    }

    public LogicExecuteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
}
