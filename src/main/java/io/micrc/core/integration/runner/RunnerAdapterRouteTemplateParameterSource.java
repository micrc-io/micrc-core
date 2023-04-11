package io.micrc.core.integration.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrc.core.integration.businesses.ApplicationCommandAdapterRouteConfiguration;
import org.apache.camel.spi.RouteTemplateParameterSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 应用业务服务适配器路由模版参数源，注入camel context，通过内部参数bean定义构造路由
 *
 * @author xwyang
 * @date 2023-04-08 16:02
 * @since 0.0.1
 */
public class RunnerAdapterRouteTemplateParameterSource implements RouteTemplateParameterSource {
    private final Map<String, RunnerAdapterRouteConfiguration.RunnerRouteTemplateParamDefinition> parameters = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> parameters(String routeId) {
        return new ObjectMapper().convertValue(parameters.get(routeId), LinkedHashMap.class);
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, RunnerAdapterRouteConfiguration.RunnerRouteTemplateParamDefinition definition) {
        parameters.put(routeId, definition);
    }

    public RunnerAdapterRouteConfiguration.RunnerRouteTemplateParamDefinition parameter(String routeId) {
        return parameters.get(routeId);
    }
}
