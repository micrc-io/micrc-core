package io.micrc.testcontainers.redistack;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;

import lombok.experimental.UtilityClass;

/**
 * set environment properties
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-01 20:24
 */
@SuppressWarnings("all")
@UtilityClass
class EnvUtils {
    static Map<String, Object> registerRedisEnvironment(ConfigurableEnvironment environment,
                                                        GenericContainer redistack,
                                                        RedistackProperties properties,
                                                        int port,
                                                        int httpPort) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("embedded.redistack.port", port);
        map.put("embedded.redistack.host", properties.getHost());
        map.put("embedded.redistack.httpPort", httpPort);
        map.put("embedded.redistack.password", properties.getPassword());
        MapPropertySource propertySource = new MapPropertySource("embeddedRedistackInfo", map);
        environment.getPropertySources().addFirst(propertySource);
        return map;
    }
}
