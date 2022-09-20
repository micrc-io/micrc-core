package io.micrc.core.integration.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.spi.RouteTemplateParameterSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 消息接收适配器路由模版参数源，注入camel context，通过内部参数bean定义构造路由
 *
 * @author tengwang
 * @date 2022-08-27 21:02
 * @since 0.0.1
 */
public class MessageAdapterRouteTemplateParameterSource implements RouteTemplateParameterSource {
    private final Map<String, MessageAdapterRouteConfiguration.ApplicationMessageRouteTemplateParamDefinition> parameters = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> parameters(String routeId) {
        return new ObjectMapper().convertValue(parameters.get(routeId), LinkedHashMap.class);
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, MessageAdapterRouteConfiguration.ApplicationMessageRouteTemplateParamDefinition definition) {
        parameters.put(routeId, definition);
    }

    public MessageAdapterRouteConfiguration.ApplicationMessageRouteTemplateParamDefinition parameter(String routeId) {
        return parameters.get(routeId);
    }
}
