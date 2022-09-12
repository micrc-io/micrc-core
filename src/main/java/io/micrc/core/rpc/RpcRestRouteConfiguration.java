package io.micrc.core.rpc;

import org.apache.camel.builder.RouteBuilder;

/**
 * rpc rest端口请求路由，服务端点路由模版定义
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-13 07:01
 */
public class RpcRestRouteConfiguration extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // TODO 1. 通用请求路由，定义req协议（rest-openapi组件），由业务/展示逻辑中进行衍生集成时使用

        // TODO 2. 服务端点路由模版，rest协议（rest组件），定义rpc接入点路由，调用integration的三个服务协议（direct组件）
    }
    
}
