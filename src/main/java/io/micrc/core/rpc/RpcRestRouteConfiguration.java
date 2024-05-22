package io.micrc.core.rpc;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * rpc rest端口请求路由，服务端点路由模版定义
 *
 * @author weiguan
 * @date 2022-09-13 07:01
 * @since 0.0.1
 */
public class RpcRestRouteConfiguration extends MicrcRouteBuilder {

    public static final String _FILE_PATH = "_filePath";

    @Autowired
    private Environment env;

    public static final String ROUTE_TMPL_REST =
            RpcRestRouteConfiguration.class.getName() + ".rest";

    @Override
    public void configureRoute() throws Exception {

        // 逻辑请求路由，定义req协议
        from("req://logic")
                .setHeader(HttpHeaders.CONTENT_TYPE, constant("application/json"))
                .toD("rest://post:/${header.logic}?host=localhost:8888")
                .convertBodyTo(String.class)
                .end();

        // 通用请求路由，定义req协议，由业务/展示逻辑中进行衍生集成时使用
        from("req://integration")
                .marshal().json().convertBodyTo(String.class)
                .setProperty("requestBody", body())
                .bean(IntegrationsInfo.class, "get(${exchange.properties.get(protocolFilePath)})")
                .setProperty("integrationInfo", body())
                .setHeader(HttpHeaders.AUTHORIZATION, constant("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJwZXJtaXNzaW9ucyI6WyIqOioiXSwidXNlcm5hbWUiOiItMSJ9.N97J1cv1Io02TLwAekzOoDHRFrnGOYeXCUiDhbAYBYY"))
                .setHeader(_FILE_PATH, exchangeProperty("protocolFilePath")) // 用于获取期望的MOCK值
                .setBody(exchangeProperty("requestBody"))
                .removeProperty("requestBody")
                .process(exchange -> {
                    IntegrationsInfo.Integration integrationInfo = exchange.getProperty("integrationInfo", IntegrationsInfo.Integration.class);
                    exchange.setProperty("url", "/api" + integrationInfo.getUrl());
                    exchange.setProperty("host", spliceHost(integrationInfo.getXHost()));
                    // 传递业务逻辑/交互逻辑的origin信息到衍生逻辑
                    String buffer = (String) exchange.getProperty("buffer");
                    String commandJson = (String) exchange.getProperty("commandJson");
                    String originHost = null;
                    if (buffer != null) {
                        originHost = (String) JsonUtil.readPath(buffer, "/_param/_originHost");
                    } else if (commandJson != null) {
                        originHost = (String) JsonUtil.readPath(commandJson, "/_originHost");
                    }
                    exchange.getIn().setHeader("origin", originHost);
                })
                .toD("rest-openapi-ssl://${exchange.properties.get(integrationInfo).getProtocolFilePath()}#${exchange.properties.get(integrationInfo).getOperationId()}?host=${exchange.properties.get(host)}&basePath=${exchange.properties.get(url)}")
                .convertBodyTo(String.class)
                .end();

        // 服务端点路由模版，rest协议（rest组件），定义rpc接入点路由，调用integration的三个服务协议（direct组件）
        routeTemplate(ROUTE_TMPL_REST)
                .templateParameter("adapterName", null, "the message adapter name")
                .templateParameter("method", null, "the method name")
                .templateParameter("address", null, "the address")
                .templateParameter("routeProtocol", null, "the route protocol")
                .templateParameter("subjectPath", null, "the subject path in body")
                .from("rest:{{method}}:{{address}}")
                .convertBodyTo(String.class)
                .setHeader("subjectPath", constant("{{subjectPath}}"))
                .process(exchange -> {
                    String body = (String) exchange.getIn().getBody();
                    if (body == null) {
                        body = "{}";
                    }
                    // 获取远程用户信息
                    String userParam = patchRemoteUserToSubjectPath(exchange, body);
                    // 对于object格式的json请求参数追加时间戳和主机信息
                    if (userParam.startsWith("{") && userParam.endsWith("}")) {
                        userParam = JsonUtil.add(userParam, "/_timestamp", String.valueOf(System.currentTimeMillis()));
                        userParam = JsonUtil.add(userParam, "/_originHost", JsonUtil.writeValueAsString(getOriginHost(exchange)));
                        exchange.getIn().setBody(userParam);
                    }
                })
                .to("{{routeProtocol}}://{{adapterName}}")
                .marshal().json().convertBodyTo(String.class)
                .end();
    }

    private String getOriginHost(Exchange exchange) {
        String origin = (String) exchange.getIn().getHeader("origin");
        if (origin == null || origin.isEmpty()) {
            return "";
        }
        String[] split = origin.split(":");
        return split[0] + ":" + split[1];
    }

    private String patchRemoteUserToSubjectPath(Exchange exchange, String body) {
        String xSubjectPath = (String) exchange.getIn().getHeader("subjectPath");
        if (xSubjectPath.isEmpty()) {
            return body;
        }
        Object camelHttpServletRequest = exchange.getIn().getHeader("CamelHttpServletRequest");
        if (camelHttpServletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) camelHttpServletRequest;
            String remoteUser = httpServletRequest.getRemoteUser();
            try {
                body = JsonUtil.add(body, xSubjectPath, remoteUser);
            } catch (Exception e) {
                body = JsonUtil.patch(body, xSubjectPath, remoteUser);
            }
        }
        return body;
    }

    private String spliceHost(String xHost) {
        Optional<String> profileStr = Optional.ofNullable(env.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        if (profiles.contains("default") || profiles.contains("local")) {
            return "http://localhost:1080";
        }
        if (xHost == null) {
            throw new RuntimeException("x-host not exists");
        }
        String[] split = xHost.split("\\.");
        if (split.length != 3) {
            throw new RuntimeException("x-host invalid");
        }
        String product = split[0];
        String domain = split[1];
        String context = split[2];
        if (domain.equals(env.getProperty("micrc.domain"))
                && context.equals(Objects.requireNonNull(env.getProperty("spring.application.name")).replace("-service", ""))) {
            return "http://localhost:" + env.getProperty("local.server.port");
        }
        return "http://" + context + "-service." + product + "-" + domain + "-" + profiles.get(0) + ".svc.cluster.local";
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

        /**
         * 当前登陆用户的位置
         */
        private String subjectPath;
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