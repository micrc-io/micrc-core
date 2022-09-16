package io.micrc.core._camel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.micrc.core.framework.json.JsonUtil;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

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

    @Bean("objectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

    @Bean
    public RoutesBuilder jsonPatchComp() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("json-patch://command")
                        .bean("jsonPatchComp", "command(${in.body}, ${in.header.command})")
                        .end();
                from("json-patch://select")
                        .bean("jsonPatchComp", "select(${in.body}, ${in.header.pointer})")
                        .end();
                from("json-patch://patch")
                        .bean("jsonPatchComp", "patch(${in.body}, ${in.header.path}, ${in.header.value})")
                        .end();
                from("json-patch://add")
                        .bean("jsonPatchComp", "add(${in.body}, ${in.header.path}, ${in.header.value})")
                        .end();
            }

            public String command(String original, String command) {
                try {
                    JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(command));
                    return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
                } catch (IOException | JsonPatchException e) {
                    throw new RuntimeException("patch fail... please check object...");
                }
            }

            public String select(String original, String pointer) {
                JsonNode originalJsonNode = JsonUtil.readTree(original);
                JsonPointer jsonPointer = JsonPointer.compile(pointer);
                return JsonUtil.writeValueAsStringRetainNull(originalJsonNode.at(jsonPointer));
            }

            public String patch(String original, String path, String value) {
                try {
                    String pathReplaced = patch.replace("{{path}}", path);
                    String valueReplaced = pathReplaced.replace("{{value}}", value);
                    JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                    return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
                } catch (IOException | JsonPatchException e) {
                    throw new RuntimeException("patch fail... please check object...");
                }
            }

            public String add(String original, String path, String value) {
                try {
                    String pathReplaced = add.replace("{{path}}", path);
                    String valueReplaced = pathReplaced.replace("{{value}}", value);
                    JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                    return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
                } catch (IOException | JsonPatchException e) {
                    throw new RuntimeException("patch fail... please check object...");
                }
            }

        };

    }

}
