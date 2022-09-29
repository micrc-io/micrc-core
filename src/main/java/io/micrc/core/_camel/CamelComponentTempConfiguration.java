package io.micrc.core._camel;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import io.micrc.lib.JsonUtil;
import lombok.SneakyThrows;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 使用路由和direct组件，临时实现各种没有的camel组件
 *
 * @author weiguan
 * @date 2022-09-13 16:38
 * @since 0.0.1
 */
@Configuration
public class CamelComponentTempConfiguration {

    @Bean("json-patch")
    public DirectComponent jsonPatch() {
        return new DirectComponent();
    }

    @Bean("json-mapping")
    public DirectComponent jsonMapping() {
        return new DirectComponent();
    }

    @Bean
    public RoutesBuilder jsonMappingComp() {
        return new RouteBuilder() {
            @Override
            @SneakyThrows
            public void configure() throws Exception {
                from("json-mapping://file")
                        .process(exchange -> {
                            Resource[] resources = new PathMatchingResourcePatternResolver()
                                    .getResources(ResourceUtils.CLASSPATH_URL_PREFIX + exchange.getIn().getHeader("mappingFilePath"));
                            for (int i = 0; i < resources.length; i++) {
                                Resource resource = resources[i];
                                Expression expression = Parser.compileString(StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8));
                                JsonNode resultNode = expression.apply(JsonUtil.readTree(exchange.getIn().getBody()));
                                exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(resultNode));
                                exchange.getIn().removeHeader("mappingFilePath");
                                break;
                            }
                        })
                        .end();
                from("json-mapping://content")
                        .process(exchange -> {
                            Expression expression = Parser.compileString((String) exchange.getIn().getHeader("mappingContent"));
                            JsonNode resultNode = expression.apply(JsonUtil.readTree(exchange.getIn().getBody()));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(resultNode));
                            exchange.getIn().removeHeader("mappingContent");
                        })
                        .end();
            }
        };
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
                            String pointer = String.valueOf(exchange.getIn().getHeader("pointer"));
                            exchange.getIn().setBody(JsonUtil.readPath(content, pointer));
                        })
                        .end();
                from("json-patch://patch")
                        .process(exchange -> {
                            String patchStr = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
                            String pathReplaced = patchStr.replace("{{path}}", String.valueOf(exchange.getIn().getHeader("path")));
                            String valueReplaced = pathReplaced.replace("{{value}}", String.valueOf(exchange.getIn().getHeader("value")));
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                        })
                        .end();
                from("json-patch://add")
                        .process(exchange -> {
                            String addStr = "[{ \"op\": \"add\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
                            String pathReplaced = addStr.replace("{{path}}", String.valueOf(exchange.getIn().getHeader("path")));
                            String valueReplaced = pathReplaced.replace("{{value}}", String.valueOf(exchange.getIn().getHeader("value")));
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                        })
                        .end();
            }
        };
    }
}
