package io.micrc.core._camel;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Parser;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.micrc.core._camel.jit.JITDMNResult;
import io.micrc.core._camel.jit.JITDMNService;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.Result;
import io.micrc.lib.JsonUtil;
import lombok.SneakyThrows;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.support.ResourceHelper;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 使用路由和direct组件，临时实现各种没有的camel组件
 *
 * @author weiguan
 * @date 2022-09-13 16:38
 * @since 0.0.1
 */
@Configuration
public class CamelComponentTempConfiguration {

    @Autowired
    private JITDMNService jitdmnService;

    @Bean
    public RoutesBuilder jsonMappingComp() {
        return new RouteBuilder() {
            @Override
            @SneakyThrows
            public void configure() throws Exception {
                from("json-mapping://file")
                        .routeId("json-mapping-file")
                        .process(exchange -> {
                            Resource[] resources = new PathMatchingResourcePatternResolver()
                                    .getResources(ResourceUtils.CLASSPATH_URL_PREFIX + exchange.getIn().getHeader("mappingFilePath"));
                            for (int i = 0; i < resources.length; i++) {
                                Resource resource = resources[i];
                                Expression expression = Parser.compileString(StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8));
                                JsonNode resultNode = expression.apply(JsonUtil.readTree(exchange.getIn().getBody()));
                                exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(resultNode));
                                exchange.getIn().removeHeader("mappingFilePath");
                            }
                            exchange.getIn().removeHeader("mappingFilePath");
                        })
                        .end();
                from("json-mapping://content")
                        .routeId("json-mapping-content")
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
    public RoutesBuilder xmlPathComp() {
        return new RouteBuilder() {
            @Override
            @SneakyThrows
            public void configure() throws Exception {
                from("xml-path://content")
                        .routeId("xml-path-content")
                        .process(exchange -> {
                            String path = exchange.getIn().getHeader("path", String.class);
                            String body = exchange.getIn().getBody(String.class);
                            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                            XPath xPath = XPathFactory.newInstance().newXPath();
                            Document document = db.parse(new ByteArrayInputStream(body.getBytes()));
                            String result = ((Node) xPath.evaluate(path, document, XPathConstants.NODE)).getTextContent();
                            exchange.getIn().setBody(result);
                        })
                        .end();
            }
        };
    }

    @Bean
    public RoutesBuilder dynamicRouteComp() {
        return new RouteBuilder() {
            @Override
            @SneakyThrows
            public void configure() throws Exception {
                from("dynamic-route://execute")
                        .routeId("dynamic-route-execute")
                        .process(exchange -> {
                            CamelContext context = exchange.getContext();
                            Route camelRoute = context.getRoute((String) exchange.getIn().getHeader("from"));
                            if (null == camelRoute) {
                                ExtendedCamelContext ec = context.adapt(ExtendedCamelContext.class);
                                ec.getRoutesLoader().loadRoutes(ResourceHelper.fromString(exchange.getIn().getHeader("from") + ".xml", (String) exchange.getIn().getHeader("route")));
                            }
                        })
                        .toD("${header.from}")
                        .removeHeader("from")
                        .removeHeader("route")
                        .end();
            }
        };
    }

    @Bean
    public RoutesBuilder dynamicDmnComp() {
        return new RouteBuilder() {
            @Override
            @SneakyThrows
            public void configure() throws Exception {
                from("dynamic-dmn://execute")
                        .routeId("dynamic-dmn-execute")
                        .process(exchange -> {
                            String dmn = exchange.getIn().getHeader("dmn", String.class);
                            String decision = exchange.getIn().getHeader("decision", String.class);
                            String context = exchange.getIn().getBody(String.class);
                            if (!StringUtils.hasText(dmn)) {
                                throw new RuntimeException("the dmn script not have value, please check dmn....");
                            }
                            if (!StringUtils.hasText(decision)) {
                                decision = "result"; // 当不传入取那个结果的时候,默认用result的决策节点
                            }
                            JITDMNResult jitdmnResult = jitdmnService.evaluateModel(dmn, JsonUtil.writeValueAsObjectRetainNull(context, Map.class));
                            String finalDecisionName = decision;
                            Optional<DMNDecisionResult> executeResult = jitdmnResult.getDecisionResults().stream().filter(result -> result.getDecisionName().equals(finalDecisionName)).findFirst();
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(executeResult.get().getResult()));
                            exchange.getIn().removeHeader("dmn");
                            exchange.getIn().removeHeader("decision");
                        })
                        .end();
            }
        };
    }

    @Bean
    public RoutesBuilder dynamicGroovyComp() {
        return new RouteBuilder() {
            @Override
            @SneakyThrows
            public void configure() throws Exception {
                from("dynamic-groovy://execute")
                        .routeId("dynamic-groovy-execute")
                        .process(exchange -> {
                            String script = exchange.getIn().getHeader("groovy", String.class);
                            Map<String, Object> params = exchange.getIn().getBody(HashMap.class);
                            if (!StringUtils.hasText(script)) {
                                throw new RuntimeException("the script not have value, please check script....");
                            }
                            Binding binding = new Binding();
                            if (params != null) {
                                Set<String> keys = params.keySet();
                                keys.stream().forEach(key -> {
                                            binding.setProperty(key, params.get(key));
                                        }
                                );
                            }
                            Object retVal = new GroovyShell(binding).evaluate(script);
                            exchange.getIn().setBody(retVal);
                            exchange.getIn().removeHeader("groovy");
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
                        .routeId("json-patch-command")
                        .process(exchange -> {
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(exchange.getIn().getHeader("command")));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                            exchange.getIn().removeHeader("command");
                        })
                        .end();
                from("json-patch://select")
                        .routeId("json-patch-select")
                        .process(exchange -> {
                            String content = String.valueOf(exchange.getIn().getBody());
                            String pointer = String.valueOf(exchange.getIn().getHeader("pointer"));
                            exchange.getIn().setBody(JsonUtil.readPath(content, pointer));
                            exchange.getIn().removeHeader("pointer");
                        })
                        .end();
                from("json-patch://patch")
                        .routeId("json-patch-patch")
                        .process(exchange -> {
                            String patchStr = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
                            String pathReplaced = patchStr.replace("{{path}}", String.valueOf(exchange.getIn().getHeader("path")));
                            String valueReplaced = pathReplaced.replace("{{value}}", String.valueOf(exchange.getIn().getHeader("value")));
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                            exchange.getIn().removeHeader("path");
                            exchange.getIn().removeHeader("value");
                        })
                        .end();
                from("json-patch://add")
                        .routeId("json-patch-add")
                        .process(exchange -> {
                            String addStr = "[{ \"op\": \"add\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
                            String pathReplaced = addStr.replace("{{path}}", String.valueOf(exchange.getIn().getHeader("path")));
                            String valueReplaced = pathReplaced.replace("{{value}}", String.valueOf(exchange.getIn().getHeader("value")));
                            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
                            exchange.getIn().setBody(JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(exchange.getIn().getBody()))));
                            exchange.getIn().removeHeader("path");
                            exchange.getIn().removeHeader("value");
                        })
                        .end();
            }
        };
    }

    @Consume("direct://test")
    public Object test(@Body Object body) {
        System.out.println("测试" + JsonUtil.writeValueAsString(body));
        return body;
    }

    @Bean("error-handle")
    public DirectComponent errorHandle() {
        return new DirectComponent();
    }

    @Bean
    public RoutesBuilder routersBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("error-handle://system")
                        .routeId("error-handle-system")
                        .setBody(exceptionMessage())
                        .process(exchange -> {
                            log.error(exchange.getIn().getBody(String.class));
                            ErrorInfo errorInfo = new ErrorInfo();
                            errorInfo.setErrorCode("999999999");
                            errorInfo.setErrorMessage("system error");
                            exchange.getIn().setBody(new Result<>().result(errorInfo, null));
                        })
                        .marshal().json().convertBodyTo(String.class)
                        .end();

                from("error-handle://command")
                        .routeId("error-handle-command")
                        .process(exchange -> {
                            log.error(exchange.getIn().getBody(String.class));
                            ErrorInfo errorInfo = new ErrorInfo();
                            errorInfo.setErrorCode("888888888");
                            errorInfo.setErrorMessage("command error");
                            String body = exchange.getIn().getBody(String.class);
                            exchange.getIn().setBody(new Result<>().result(errorInfo, JsonUtil.writeValueAsObject(body, Object.class)));
                        })
                        .marshal().json().convertBodyTo(String.class)
                        .end();
            }
        };
    }
}
