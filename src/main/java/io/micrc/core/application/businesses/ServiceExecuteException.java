package io.micrc.core.application.businesses;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用服务执行异常
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/8/29 16:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceExecuteException extends RuntimeException {
    public ServiceExecuteException(String message) {
        super(message);
    }
}
