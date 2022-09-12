package io.micrc.core.message;

import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.RouteTemplateParameterSource;

/**
 * 消息订阅路由模版参数源
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-13 05:56
 */
public class MessageRouteTemplateParameterSource implements RouteTemplateParameterSource {

    @Override
    public Map<String, Object> parameters(String routeId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<String> routeIds() {
        // TODO Auto-generated method stub
        return null;
    }
    
}
