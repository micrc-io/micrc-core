package io.micrc.core.rpc;

import lombok.Data;

/**
 * 异常信息
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/9/15 20:24
 */
@Data
public class ErrorInfo {

    /**
     * 执行过程中的错误编码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;
}
