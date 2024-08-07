package io.micrc.core._camel;

import com.github.fge.jsonpatch.JsonPatch;
import freemarker.template.Template;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import io.micrc.core._camel.jit.JITDMNResult;
import io.micrc.core._camel.jit.JITDMNService;
import io.micrc.core.authorize.MyRealm;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.Result;
import io.micrc.lib.*;
import lombok.SneakyThrows;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ResourceHelper;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 使用路由和direct组件，临时实现各种没有的camel组件
 *
 * @author weiguan
 * @date 2022-09-13 16:38
 * @since 0.0.1
 */
@Configuration
public class CamelComponentTempConfiguration {

    public static final String USER_VERIFY_KEY_PREFIX = "USER:VERIFY:";

    static final ExpressionParser parser = new SpelExpressionParser();
    static final TemplateParserContext parserContext = new TemplateParserContext();
    static final MapAccessor mapAccessor = new MapAccessor();

    static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    static final XPath xPath = XPathFactory.newInstance().newXPath();

    static final freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_31);


    @Autowired
    private JITDMNService jitdmnService;

    @Autowired
    Environment environment;

    @javax.annotation.Resource(name = "memoryDbTemplate")
    RedisTemplate<Object, Object> redisTemplate;

    static {
        cfg.setDefaultEncoding("UTF-8");
    }

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
                                String jslt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                                exchange.getIn().setBody(JsonUtil.transform(jslt, exchange.getIn().getBody()));
                                exchange.getIn().removeHeader("mappingFilePath");
                            }
                            exchange.getIn().removeHeader("mappingFilePath");
                        })
                        .end();
                from("json-mapping://content")
                        .routeId("json-mapping-content")
                        .process(exchange -> {
                            String jslt = (String) exchange.getIn().getHeader("mappingContent");
                            exchange.getIn().setBody(JsonUtil.transform(jslt, exchange.getIn().getBody()));
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
                            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                            String xml = exchange.getIn().getHeader("xml", String.class);
                            String path = exchange.getIn().getBody(String.class);
                            if (xml == null || path == null) {
                                exchange.getIn().setBody(null);
                                return;
                            }
                            String result = null;
                            if (path.startsWith("\"")) {
                                path = (String) JsonUtil.readPath(path, "");
                                Document document = documentBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
                                result = ((Node) xPath.evaluate(path, document, XPathConstants.NODE)).getTextContent();
                            } else if (path.startsWith("[") && path.endsWith("]")) {
                                List<String> paths = JsonUtil.writeValueAsList(path, String.class);
                                List<String> collect = paths.stream().map(p -> {
                                    try {
                                        Document document = documentBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
                                        return ((Node) xPath.evaluate(p, document, XPathConstants.NODE)).getTextContent();
                                    } catch (Exception e) {
                                        return null;
                                    }
                                }).collect(Collectors.toList());
                                result = JsonUtil.writeValueAsString(collect);
                            }
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
                            String route = (String) exchange.getIn().getHeader("route");
                            Object from = exchange.getIn().getHeader("from");
                            String variable = exchange.getIn().getHeader("variable", String.class);
                            // 默认接收json参数，允许接收""包裹的json参数
                            if (JsonUtil.validate(variable)) {
                                Object bodyObj = JsonUtil.readPath(variable, "");
                                // 处理""包裹的json参数
                                if (bodyObj instanceof String && JsonUtil.validate((String) bodyObj)) {
                                    bodyObj = JsonUtil.readPath((String) bodyObj, "");
                                }
                                if (bodyObj instanceof HashMap) {
                                    HashMap<String, Object> hashMap = (HashMap<String, Object>) bodyObj;
                                    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(hashMap);
                                    evaluationContext.addPropertyAccessor(mapAccessor);
                                    route = parser.parseExpression(route, parserContext).getValue(evaluationContext, String.class);
//                                    route = parser.parseExpression(route, parserContext).getValue(hashMap, String.class);
                                }
                            }
                            CamelContext context = exchange.getContext();
                            Route camelRoute = context.getRoute((String) from);
                            if (null == camelRoute) {
                                ExtendedCamelContext ec = context.adapt(ExtendedCamelContext.class);
                                ec.getRoutesLoader().loadRoutes(ResourceHelper.fromString(from + ".xml", route));
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
                            if (!StringUtils.hasText(script)) {
                                throw new RuntimeException("the script not have value, please check script....");
                            }
                            Binding binding = new Binding();
                            binding.setProperty("params", exchange.getIn().getBody());
                            Object retVal = new GroovyShell(binding).evaluate(script);
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(retVal));
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

    @Bean
    public RoutesBuilder authorizeBuilders() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("authorize://authentication")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            Object identity = JsonUtil.readPath(body, "/identity");
                            if (identity == null) {
                                throw new RuntimeException("[identity] must not be null");
                            }
                            List<String> permissions = ClassCastUtils.castArrayList(JsonUtil.readPath(body, "/permissions"), String.class);// 概念名
                            if (permissions == null) {
                                throw new RuntimeException("[permissions] must not be null");
                            }
                            String token = JwtUtil.createToken(identity.toString(), permissions.toArray(new String[0]), MyRealm.TOKEN_EXPIRE_TIME);
                            String key = MyRealm.USER_PERMISSIONS_KEY_PREFIX + identity;
                            redisTemplate.opsForValue().set(key, permissions, MyRealm.TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(token));
                        })
                        .end();

                from("authorize://decertification")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            Object identity = JsonUtil.readPath(body, "/identity");
                            if (identity == null) {
                                throw new RuntimeException("[identity] must not be null");
                            }
                            String key = MyRealm.USER_PERMISSIONS_KEY_PREFIX + identity;
                            redisTemplate.delete(key);
                            Boolean has = redisTemplate.hasKey(key);
                            boolean success = has == null || !has;
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(success));
                        })
                        .end();

                from("authorize://pbkdf2Encrypt")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            String password = (String) JsonUtil.readPath(body, "/password");
                            if (password == null) {
                                throw new RuntimeException("[password] must not be null");
                            }
                            String salt = (String) JsonUtil.readPath(body, "/salt");
                            if (salt == null) {
                                throw new RuntimeException("[salt] must not be null");
                            }
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(EncryptUtils.pbkdf2(password, salt)));
                        })
                        .end();

                from("authorize://generateSalt")
                        .process(exchange -> {
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(EncryptUtils.generateSalt()));
                        })
                        .end();

                from("authorize://generateVerifyCode")
                        .process(exchange -> {
                            ValidateCodeUtil.Validate randomCode = ValidateCodeUtil.getRandomCode();
                            redisTemplate.opsForValue().set(USER_VERIFY_KEY_PREFIX + randomCode.getKey(), randomCode.getValue(),5 , TimeUnit.MINUTES);
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(randomCode));
                        })
                        .end();

                from("authorize://compareVerifyCode")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            String key = (String) JsonUtil.readPath(body, "/key");
                            if (key == null) {
                                throw new RuntimeException("[key] must not be null");
                            }
                            String value = (String) JsonUtil.readPath(body, "/value");
                            if (value == null) {
                                throw new RuntimeException("[value] must not be null");
                            }
                            key = USER_VERIFY_KEY_PREFIX + key;
                            String truthValue = (String) redisTemplate.opsForValue().get(key);
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(value.equalsIgnoreCase(truthValue)));
                            redisTemplate.delete(key);
                        })
                        .end();
            }
        };
    }

    @Bean
    public RoutesBuilder executeBuilders() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct://getActiveProfiles")
                        .process(exchange -> {
                            Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
                            List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(profiles));
                        })
                        .end();

                from("direct://replaceTemplateKey")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            body = body.replaceAll("\n","");
                            Object template = JsonUtil.readPath(body, "/template");
                            if (template == null) {
                                throw new RuntimeException("[template] must not be null");
                            }
                            Object value = JsonUtil.readPath(body, "/value");
                            if (value == null) {
                                throw new RuntimeException("[value] must not be null");
                            }
                            StringWriter out = new StringWriter();
                            new Template("template", new StringReader((String) template), cfg).process(value, out);
                            String result = out.toString();
                            exchange.getIn().setBody(JsonUtil.writeValueAsString(result));
                        })
                        .end();

                // 以下路由为路由脚本文件中使用的特定路由，返回结果不受形式限制

                from("direct://base64Encode")
                        .process(exchange -> {
                            String base64 = Base64.getEncoder().encodeToString(exchange.getIn().getBody(String.class).getBytes());
                            exchange.getIn().setBody(base64);
                        })
                        .end();

                from("direct://hmacsha256Encrypt")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            String data = (String) JsonUtil.readPath(body, "/data");
                            if (data == null) {
                                throw new RuntimeException("[data] must not be null");
                            }
                            String key = (String) JsonUtil.readPath(body, "/key");
                            if (key == null) {
                                throw new RuntimeException("[key] must not be null");
                            }
                            exchange.getIn().setBody(EncryptUtils.HMACSHA256(data, key));
                        })
                        .end();
            }
        };
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
                            ErrorInfo errorInfo = new ErrorInfo();
                            errorInfo.setErrorCode("999999999");
                            errorInfo.setErrorMessage(getErrorMessage(exchange));
                            exchange.getIn().setBody(new Result<>().result(errorInfo, null));
                        })
                        .marshal().json().convertBodyTo(String.class)
                        .end();

                from("error-handle://command")
                        .routeId("error-handle-command")
                        .process(exchange -> {
                            String message = getErrorMessage(exchange);
                            ErrorInfo errorInfo = new ErrorInfo();
                            errorInfo.setErrorCode(message);
                            errorInfo.setErrorMessage(message);
                            patchErrorToCommand(exchange, errorInfo);
                        })
                        .end();

                from("error-handle://business")
                        .routeId("error-handle-business")
                        .process(exchange -> {
                            ErrorInfo errorInfo = new ErrorInfo();
                            errorInfo.setErrorCode("888888888");
                            errorInfo.setErrorMessage(getErrorMessage(exchange));
                            patchErrorToCommand(exchange, errorInfo);
                        })
                        .end();
            }

            private void patchErrorToCommand(Exchange exchange, ErrorInfo errorInfo) {
                String commandJson = (String) exchange.getProperty("commandJson");
                commandJson = JsonUtil.patch(commandJson, "/error", JsonUtil.writeValueAsString(errorInfo));
                exchange.setProperty("commandJson", commandJson);
                Object command = exchange.getProperty("command");
                BeanUtils.copyProperties(JsonUtil.writeValueAsObject(commandJson, command.getClass()), command);
            }

            private String getErrorMessage(Exchange exchange) {
                Throwable throwable = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                if (!StringUtils.hasText(throwable.getLocalizedMessage()) && null != throwable.getCause()) {
                    throwable = throwable.getCause();
                }
                if (InvocationTargetException.class.equals(throwable.getClass())) {
                    throwable = ((InvocationTargetException) throwable).getTargetException();
                }
                if (null != throwable) {
                    throwable.printStackTrace();
                    String message = throwable.getLocalizedMessage();
                    log.error(message);
                    return message;
                }
                return "unknown error";
            }
        };
    }
}
