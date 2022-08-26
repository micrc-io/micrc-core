package io.micrc.core.application.businesses;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.RouteTemplateParameterSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.ApplicationBusinessesServiceDefinition;

public class ApplicationBusinessesServiceRouteTemplateParameterSource implements RouteTemplateParameterSource {
    private final Map<String, ApplicationBusinessesServiceDefinition> parameters = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> parameters(String routeId) {
        return new ObjectMapper().convertValue(parameters.get(routeId), LinkedHashMap.class);
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, ApplicationBusinessesServiceDefinition definition) {
        parameters.put(routeId, definition);
    }

    public ApplicationBusinessesServiceDefinition parameter(String routeId) {
        return parameters.get(routeId);
    }
}
