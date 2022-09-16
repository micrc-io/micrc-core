package io.micrc.core.application.businesses;

import io.micrc.core.rpc.ErrorInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 基础Command
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2021/10/12 1:32 下午
 */
@Data
@Slf4j
public abstract class Command {

    /**
     * 事件名称
     */
    private String event;

    /**
     * 异常信息
     */
    private ErrorInfo error;
}
