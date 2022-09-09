package io.micrc.core.application.businesses;

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
public abstract class Command<T> {

    /**
     * 事件名称
     */
    private String event;

    /**
     * 执行过程中的错误编码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;
}
