package io.micrc.core._camel;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import io.micrc.core.framework.json.JsonUtil;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用路由和direct组件，临时实现各种没有的camel组件
 *
 * @author weiguan
 * @date 2022-09-13 16:38
 * @since 0.0.1
 */
@Configuration
public class CamelComponentTempConfiguration {

    public static final String add = "[{ \"op\": \"add\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

    public static final String patch = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

    @Bean("json-patch")
    public DirectComponent jsonPatch() {
        return new DirectComponent();
    }

    @Bean
    public RoutesBuilder jsonPatchComp() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("json-patch://command")
                        .process(exchange -> {
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(exchange.getIn().getHeader("command")));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                        })
                        .end();
                from("json-patch://select")
                        .process(exchange -> {
                            String content = String.valueOf(exchange.getIn().getBody());
                            JsonNode originalJsonNode = JsonUtil.readTree(content);
                            JsonPointer jsonPointer = JsonPointer.compile(String.valueOf(exchange.getIn().getHeader("pointer")));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(originalJsonNode.at(jsonPointer)));
                        })
                        .end();
                from("json-patch://patch")
                        .process(exchange -> {
                            String patchStr = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
                            String pathReplaced = patchStr.replace("{{path}}", String.valueOf(exchange.getIn().getHeader("path")));
                            String valueReplaced = pathReplaced.replace("{{value}}", String.valueOf(exchange.getIn().getHeader("header")));
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                        })
                        .end();
                from("json-patch://add")
                        .process(exchange -> {
                            String addStr = "[{ \"op\": \"add\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
                            String pathReplaced = addStr.replace("{{path}}", String.valueOf(exchange.getIn().getHeader("path")));
                            String valueReplaced = pathReplaced.replace("{{value}}", String.valueOf(exchange.getIn().getHeader("header")));
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                        })
                        .end();
            }
        };

    }

}
