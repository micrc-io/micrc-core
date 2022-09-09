package io.micrc.core.integration.command.message;

/**
 * 消息适配器父接口，所有需要被支撑库支持的消息接收适配，都必须实现这个接口
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
public interface MessageIntegrationAdapter {
    void adapt(String command);
}
