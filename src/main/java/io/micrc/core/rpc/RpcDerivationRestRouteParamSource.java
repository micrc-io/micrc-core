package io.micrc.core.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import org.apache.camel.spi.RouteTemplateParameterSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 路由模版参数源
 *
 * @author weiguan
 * @date 2022-09-13 05:56
 * @since 0.0.1
 */
public class RpcDerivationRestRouteParamSource implements RouteTemplateParameterSource {

    private final Map<String, AbstractRouteTemplateParamDefinition> parameters = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> parameters(String routeId) {
        return new ObjectMapper().convertValue(parameters.get(routeId), LinkedHashMap.class);
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, AbstractRouteTemplateParamDefinition definition) {
        parameters.put(routeId, definition);
    }

    public AbstractRouteTemplateParamDefinition parameter(String routeId) {
        return parameters.get(routeId);
    }

}
