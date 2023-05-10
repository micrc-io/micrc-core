package io.micrc.core.rpc;

import org.apache.camel.EndpointInject;
import org.apache.camel.Headers;
import org.apache.camel.ProducerTemplate;

import java.util.Map;

/**
 * 逻辑请求,用于请求DMN逻辑
 *
 * @author hyosunghan
 * @date 2022/9/28 11:31
 * @since 0.0.1
 */
public class LogicRequest {

    @EndpointInject("req://logic")
    private ProducerTemplate template;

    public Object request(Object body, @Headers Map<String, Object> header) {
        return template.requestBodyAndHeaders(body, header);
    }
}
