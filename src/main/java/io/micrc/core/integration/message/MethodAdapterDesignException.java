package io.micrc.core.integration.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息适配器设计异常
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/8/29 16:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MethodAdapterDesignException extends RuntimeException {
    public MethodAdapterDesignException(String message) {
        super(message);
    }
}
