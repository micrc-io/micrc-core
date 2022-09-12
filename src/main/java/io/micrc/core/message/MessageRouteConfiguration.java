package io.micrc.core.message;

import org.apache.camel.builder.RouteBuilder;

/**
 * 消息存储、发送路由，订阅路由模版定义
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-13 05:35
 */
public class MessageRouteConfiguration extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        // TODO 1. 存储事件通用路由，业务逻辑应该使用这个路由发送事件（保存事件）。创建direct组件命名为eventstore协议

        // TODO 2. 调度转发消息通用路由，从事件表中获取消息，使用消息跟踪表控制发送，使用publish协议（spring-rabbitmq组件）发送

        // TODO 3. 事件订阅路由模版，使用subscribe协议（spring-rabbitmq组件）监听
        //         鉴别消息的事件类型，执行幂等，调用integration中的消息监听适配协议（message）
    }
}
