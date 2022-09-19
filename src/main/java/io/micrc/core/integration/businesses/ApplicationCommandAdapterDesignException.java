package io.micrc.core.integration.businesses;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务适配器设计异常
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/8/29 16:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationCommandAdapterDesignException extends RuntimeException {
    public ApplicationCommandAdapterDesignException(String message) {
        super(message);
    }
}
