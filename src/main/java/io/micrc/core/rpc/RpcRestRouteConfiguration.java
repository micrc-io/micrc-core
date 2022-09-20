package io.micrc.core.rpc;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;

/**
 * rpc rest端口请求路由，服务端点路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 07:01
 * @since 0.0.1
 */
public class RpcRestRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_REST_COMMAND =
            RpcRestRouteConfiguration.class.getName() + ".restCommand";

    public static final String ROUTE_TMPL_REST_DERIVATION =
            RpcRestRouteConfiguration.class.getName() + ".restDerivation";

    public static final String ROUTE_TMPL_REST_PRESENTATION =
            RpcRestRouteConfiguration.class.getName() + ".restPresentation";

    @Override
    public void configureRoute() throws Exception {
        // TODO 1. 通用请求路由，定义req协议（rest-openapi组件），由业务/展示逻辑中进行衍生集成时使用

        // 服务端点路由模版，rest协议（rest组件），定义rpc接入点路由，调用integration的三个服务协议（direct组件）
        routeTemplate(ROUTE_TMPL_REST_COMMAND)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("method", null, "the method name")
                .templateParameter("address", null, "the address")
                .templateParameter("routeProtocol", null, "the route protocol")
                .from("rest:{{method}}:{{address}}")
                .convertBodyTo(String.class)
                .to("{{routeProtocol}}://{{adapterName}}")
                .end();
        routeTemplate(ROUTE_TMPL_REST_DERIVATION)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("method", null, "the method name")
                .templateParameter("address", null, "the address")
                .templateParameter("routeProtocol", null, "the route protocol")
                .from("rest:{{method}}:{{address}}")
                .convertBodyTo(String.class)
                .to("{{routeProtocol}}://{{adapterName}}")
                .end();
        routeTemplate(ROUTE_TMPL_REST_PRESENTATION)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("method", null, "the method name")
                .templateParameter("address", null, "the address")
                .templateParameter("routeProtocol", null, "the route protocol")
                .from("rest:{{method}}:{{address}}")
                .convertBodyTo(String.class)
                .to("{{routeProtocol}}://{{adapterName}}")
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

        private HashMap<String, RpcDefinition> adaptersInfo = new HashMap<>();

        public AdaptersInfo(AdaptersInfo adaptersInfo) {
            this.adaptersInfo = adaptersInfo.adaptersInfo;
        }

        public void put(String key, RpcDefinition value) {
            this.adaptersInfo.put(key, value);
        }

        public RpcDefinition get(String key) {
            return adaptersInfo.get(key);
        }
    }
}
