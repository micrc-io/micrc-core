package io.micrc.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.spi.RouteTemplateParameterSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 消息MOCK发送路由模版参数源，注入camel context，通过内部参数bean定义构造路由
 *
 * @author hyosunghan
 * @date 2022-11-19 14:21
 * @since 0.0.1
 */
public class MessageMockSenderRouteTemplateParameterSource implements RouteTemplateParameterSource {
    private final Map<String, MessageMockSenderRouteConfiguration.MessageMockSenderDefinition> parameters = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> parameters(String routeId) {
        return new ObjectMapper().convertValue(parameters.get(routeId), LinkedHashMap.class);
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, MessageMockSenderRouteConfiguration.MessageMockSenderDefinition definition) {
        parameters.put(routeId, definition);
    }

    public MessageMockSenderRouteConfiguration.MessageMockSenderDefinition parameter(String routeId) {
        return parameters.get(routeId);
    }
}
