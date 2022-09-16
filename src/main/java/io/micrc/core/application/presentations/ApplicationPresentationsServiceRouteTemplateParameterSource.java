package io.micrc.core.application.presentations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.spi.RouteTemplateParameterSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 展示业务服务路由模版参数源，注入camel context，通过内部参数bean定义构造路由
 *
 * @author hyosunghan
 * @date 2022-09-05 11:40
 * @since 0.0.1
 */
public class ApplicationPresentationsServiceRouteTemplateParameterSource implements RouteTemplateParameterSource {
    private final Map<String, ApplicationPresentationsServiceRouteConfiguration.ApplicationPresentationsServiceDefinition> parameters = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> parameters(String routeId) {
        return new ObjectMapper().convertValue(parameters.get(routeId), LinkedHashMap.class);
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, ApplicationPresentationsServiceRouteConfiguration.ApplicationPresentationsServiceDefinition definition) {
        parameters.put(routeId, definition);
    }

    public ApplicationPresentationsServiceRouteConfiguration.ApplicationPresentationsServiceDefinition parameter(String routeId) {
        return parameters.get(routeId);
    }
}
