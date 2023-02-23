package io.micrc.core.rpc;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.*;

/**
 * rpc rest端口请求路由，服务端点路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 07:01
 * @since 0.0.1
 */
public class RpcRestRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_REST =
            RpcRestRouteConfiguration.class.getName() + ".rest";

    @Override
    public void configureRoute() throws Exception {
        /**
         header:    openApiPath openapi文件路径
                    operatorId 操作ID
                    host 主机地址
         */
        from("rest-openapi-executor://https")
                .routeId("rest-openapi-executor-https")
                .toD("rest-openapi-ssl://${exchange.properties.get(openapiInfo).get(openApiPath)}#${exchange.properties.get(openapiInfo).get(operatorId)}?host=${exchange.properties.get(openapiInfo).get(host)}")
                .convertBodyTo(String.class)
                .end();
        /**
         header:    openApiPath openapi文件路径
                    operatorId 操作ID
                    host 主机地址
         */
        from("rest-openapi-executor://http")
                .routeId("rest-openapi-executor-http")
                .toD("rest-openapi://${exchange.properties.get(openapiInfo).get(openApiPath)}#${exchange.properties.get(openapiInfo).get(operatorId)}?host=${exchange.properties.get(openapiInfo).get(host)}")
                .convertBodyTo(String.class)
                .end();

        // 逻辑请求路由，定义req协议
        from("req://logic")
                .toD("rest://post:/${header.logic}?host=localhost:8888")
                .convertBodyTo(String.class)
                .end();

        // 通用请求路由，定义req协议，由业务/展示逻辑中进行衍生集成时使用
        from("req://integration")
                .marshal().json().convertBodyTo(String.class)
                .setProperty("requestBody", body())
                .bean(IntegrationsInfo.class, "get(${exchange.properties.get(protocolFilePath)})")
                .setProperty("integrationInfo", body())
                .setBody(exchangeProperty("requestBody"))
                .removeProperty("requestBody")
                // 因衍生服务提供端会自行添加/api的基础路径,故在集成衍生时,则使用自行协议,添加/api头
                .toD("rest-openapi-derive://${exchange.properties.get(integrationInfo).getProtocolFilePath()}#${exchange.properties.get(integrationInfo).getOperationId()}?host=${exchange.properties.get(integrationInfo).getHost()}")
                .convertBodyTo(String.class)
                .end();

        // 服务端点路由模版，rest协议（rest组件），定义rpc接入点路由，调用integration的三个服务协议（direct组件）
        routeTemplate(ROUTE_TMPL_REST)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("method", null, "the method name")
                .templateParameter("address", null, "the address")
                .templateParameter("routeProtocol", null, "the route protocol")
                .from("rest:{{method}}:{{address}}")
                .convertBodyTo(String.class)
                .to("{{routeProtocol}}://{{adapterName}}")
                .marshal().json().convertBodyTo(String.class)
                .end();
    }


    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class RpcDefinition extends AbstractRouteTemplateParamDefinition {

        /**
         * 适配器名称
         */
        private String adapterName;

        /**
         * 方法名称
         */
        private String method;

        /**
         * 事件名称
         */
        private String address;

        /**
         * OpenApi协议路径
         */
        private String protocolPath;

        /**
         * 路由协议
         */
        private String routeProtocol;
    }

    @NoArgsConstructor
    public static class AdaptersInfo {

        private final Map<String, RpcDefinition> caches = new HashMap<>();

        private List<RpcDefinition> adaptersInfo = new ArrayList<>();

        public void add(RpcDefinition rpcDefinition) {
            this.adaptersInfo.add(rpcDefinition);
        }

        public RpcDefinition get(String protocolFilePath) {
            RpcDefinition rpcDefinition = caches.get(protocolFilePath);
            if (null == rpcDefinition) {
                Optional<RpcDefinition> adapter = adaptersInfo.stream().filter(adapterInfo -> protocolFilePath.equals(adapterInfo.protocolPath)).findFirst();
                caches.put(adapter.get().getProtocolPath(), adapter.get());
            }
            return caches.get(protocolFilePath);
        }

        public List<RpcDefinition> getAll() {
            return this.adaptersInfo;
        }
    }
}