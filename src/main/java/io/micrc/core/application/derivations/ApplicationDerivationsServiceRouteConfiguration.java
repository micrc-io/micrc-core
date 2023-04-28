package io.micrc.core.application.derivations;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.annotations.application.derivations.TechnologyType;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.FileUtils;
import io.micrc.lib.JsonUtil;
import io.micrc.lib.StringUtil;
import io.micrc.lib.TimeReplaceUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 应用衍生服务路由定义和参数bean定义
 * 衍生服务具体执行逻辑由模版定义，外部注解通过属性声明逻辑变量，由这些参数和路由模版构造执行路由
 *
 * @author hyosunghan
 * @date 2022-09-19 11:23
 * @since 0.0.1
 */
public class ApplicationDerivationsServiceRouteConfiguration extends MicrcRouteBuilder {
    // 路由模版ID
    public static final String ROUTE_TMPL_DERIVATIONS_SERVICE =
            ApplicationDerivationsServiceRouteConfiguration.class.getName() + ".derivationsService";

    /**
     * 配置衍生服务路由模板
     * 1，设置模板参数
     * 2，指定模板入口
     * 3，动态路由处理
     * 4，汇编处理结果
     * 5，标识路由结束
     *
     * @throws Exception
     */
    @Override
    public void configureRoute() throws Exception {

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_DERIVATIONS_SERVICE)
                .templateParameter("serviceName", null, "the derivations service name")
                .templateParameter("paramIntegrationsJson", null, "the command integration params")
                .templateParameter("assembler", null, "assembler")
                .templateParameter("timePathsJson", null, "time path list json")
                .from("derivations:{{serviceName}}")
                .setProperty("paramIntegrationsJson", simple("{{paramIntegrationsJson}}"))
                .setProperty("assembler", simple("{{assembler}}"))
                .setProperty("timePathsJson", simple("{{timePathsJson}}"))
                // 1.处理请求
                .setProperty("param", simple("${in.body}"))
                // 2.解析时间
                .to("direct://parse-time-derivation")
                // 3.动态集成
                .to("direct://dynamic-integration-derivation")
                // 4.处理结果
                .to("direct://handle-result-derivation")
                .end();

        from("direct://derivations-integration")
                // 得到需要集成的集成参数，todo,封装进动态路由
                .bean(IntegrationParams.class, "findExecutable(${exchange.properties.get(unIntegrateParams)}, ${exchange.properties.get(param)}, ${exchange.properties.get(timePaths)})")
                .setProperty("current", body())
                // 构造发送
                .choice()
                .when(constant("QUERY").isEqualTo(simple("${exchange.properties.get(current).get(type)}")))
                    .bean(IntegrationParams.class, "executeQuery")
                .endChoice()
                .when(constant("GENERAL_TECHNOLOGY").isEqualTo(simple("${exchange.properties.get(current).get(type)}")))
                    .setHeader("from", simple("${exchange.properties.get(current).get(routeName)}"))
                    .setHeader("script", simple("${exchange.properties.get(current).get(logic)}"))
                    .setHeader("executeType", constant("ROUTE"))
                    .setBody(simple("${exchange.properties.get(current).get(params)}"))
                    .to("dynamic-executor://execute")
                .when(constant("SPECIAL_TECHNOLOGY").isEqualTo(simple("${exchange.properties.get(current).get(type)}")))
                    .setHeader("from", simple("${exchange.properties.get(current).get(routeName)}"))
                    .setHeader("script", simple("${exchange.properties.get(current).get(logic)}"))
                    .setHeader("executeType", simple("${exchange.properties.get(current).get(technologyType)}"))
                    .setBody(simple("${exchange.properties.get(current).get(params)}"))
                    .to("dynamic-executor://execute")
                .end()
                  // 处理返回
                .bean(IntegrationParams.class, "processResult");

        from("direct://parse-time-derivation")
                .setBody(exchangeProperty("timePathsJson"))
                .process(exchange -> {
                    exchange.getIn().setBody(JsonUtil.writeValueAsList(exchange.getIn().getBody(String.class), String.class)
                            .stream().map(i -> i.split("/")).collect(Collectors.toList()));
                })
                .setProperty("timePaths", body());

        from("direct://dynamic-integration-derivation")
                .setBody(exchangeProperty("param"))
                .dynamicRouter(method(IntegrationParams.class, "dynamicIntegrate"));

        from("direct://handle-result-derivation")
                .setBody(exchangeProperty("param"))
                .setHeader("mappingContent", exchangeProperty("assembler"))
                .to("json-mapping://content");
    }

    /**
     * 应用衍生服务路由参数Bean
     *
     * @author hyosunghan
     * @date 2022-09-19 11:30
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationDerivationsServiceDefinition extends AbstractRouteTemplateParamDefinition {

        /**
         * 业务服务名称
         */
        protected String serviceName;

        /**
         * 汇编器
         */
        protected String assembler;

        /**
         * 需集成参数属性定义
         */
        protected String paramIntegrationsJson;

        /**
         * 时间路径JSON
         */
        protected String timePathsJson;
    }
}

/**
 * 集成参数
 */
@Slf4j
class IntegrationParams {

    /**
     * 动态集成所有需要的外部参数
     *
     * @param properties
     * @return
     */
    public static String dynamicIntegrate(@ExchangeProperties Map<String, Object> properties) {
        List<ParamIntegration> paramIntegrations = ClassCastUtils.castArrayList(properties.get("paramIntegrations"), ParamIntegration.class);
        // 初始化动态路由集成控制信息
        if (null == paramIntegrations) {
            paramIntegrations = JsonUtil.writeValueAsList((String) properties.get("paramIntegrationsJson"),
                    ParamIntegration.class);
            properties.put("paramIntegrations", paramIntegrations);
        }
        // 判断是否有需要集成的参数
        List<ParamIntegration> unIntegrateParams = paramIntegrations.stream()
                .filter(i -> !i.getIntegrationComplete()).collect(Collectors.toList());
        properties.put("unIntegrateParams", unIntegrateParams);
        log.info("衍生未集成：{}", unIntegrateParams.stream().map(ParamIntegration::getConcept).collect(Collectors.joining(",")));
        if (unIntegrateParams.size() == 0) {
            return null;
        }
        return "direct://derivations-integration";
    }

    /**
     * 处理当前集成结果
     *
     * @param exchange
     */
    public static void processResult(Exchange exchange) throws Exception {
        Map<String, Object> properties = ClassCastUtils.castHashMap(exchange.getProperties(), String.class, Object.class);
        Object body = exchange.getIn().getBody();
        if (body instanceof byte[]) {
            body = new String((byte[]) body);
        }
        String param = (String) properties.get("param");
        List<String[]> timePathList = ClassCastUtils.castArrayList(properties.get("timePaths"), String[].class);
        Map<String, Object> current = ClassCastUtils.castHashMap(properties.get("current"), String.class, Object.class);
        String name = (String) current.get("name");
        ParamIntegration.Type currentIntegrateType = (ParamIntegration.Type) current.get("type");
        if (ParamIntegration.Type.QUERY.equals(currentIntegrateType) && body instanceof Optional) {
            body = ((Optional<?>) body).orElse(null);
        } else if (ParamIntegration.Type.GENERAL_TECHNOLOGY.equals(currentIntegrateType)
                || ParamIntegration.Type.SPECIAL_TECHNOLOGY.equals(currentIntegrateType)) {
            body = JsonUtil.readPath((String) body, "");
        }
        log.info("衍生已集成：{}，结果：{}", name, JsonUtil.writeValueAsString(body));
        List<ParamIntegration> paramIntegrations = ClassCastUtils.castArrayList(exchange.getProperties().get("paramIntegrations"), ParamIntegration.class);
        // 将上次执行的结果放回至原有属性集成参数之中
        ParamIntegration find = paramIntegrations.stream()
                .filter(paramIntegration -> name.equals(paramIntegration.getConcept()))
                .findFirst().orElseThrow();
        // 标识该参数已成功
        find.setIntegrationComplete(true);
        String path = "/" + find.getConcept();
        String value = JsonUtil.writeValueAsString(body);
        if (TechnologyType.DMN.equals(current.get("technologyType"))) {
            value = TimeReplaceUtil.matchTimePathAndReplaceTime(timePathList, path, value, Long.class);
        }
        param = JsonUtil.add(param, path, value);
        exchange.getProperties().put("param", param);
        exchange.getProperties().put("paramIntegrations", paramIntegrations);
    }

    /**
     * 得到一个可执行的集成的集成信息
     *
     * @param unIntegrateParams
     * @param param
     * @return
     */
    public static Map<String, Object> findExecutable(List<ParamIntegration> unIntegrateParams, String param, List<String[]> timePathList)
            throws XPathExpressionException, IOException, SAXException, ParserConfigurationException {
        Map<String, Object> executableIntegrationInfo = new HashMap<>();
        int checkNumber = -1;
        do {
            // 没有可执行的集成了,抛异常
            checkNumber++;
            if (checkNumber >= unIntegrateParams.size()) {
                throw new IllegalStateException("the integration file have error, command need integrate, but the param can not use... ");
            }
            ParamIntegration paramIntegration = JsonUtil.writeObjectAsObject(unIntegrateParams.get(checkNumber), ParamIntegration.class);
            // 转换请求参数
            AtomicReference<String> value = new AtomicReference<>();
            if (ParamIntegration.Type.SPECIAL_TECHNOLOGY.equals(paramIntegration.getType())
                    || ParamIntegration.Type.GENERAL_TECHNOLOGY.equals(paramIntegration.getType())) {
                paramIntegration.getParamMappings().stream().findFirst().ifPresent(mapping -> {
                    value.set(JsonUtil.transAndCheck(mapping, param, null));
                    // 需要执行DMN的时候，时间格式需要转换
                    if (TechnologyType.DMN.equals(paramIntegration.getTechnologyType())) {
                        value.set(TimeReplaceUtil.matchTimePathAndReplaceTime(timePathList, "", value.get(), String.class));// 根目录路径为""
                    }
                });
                // 检查当前查询是否可执行
                if (null == value.get()) {
                    continue;
                }
            }
            if (ParamIntegration.Type.QUERY.equals(paramIntegration.getType())) {
                List<String> params = paramIntegration.getParamMappings().stream()
                        .map(mapping -> JsonUtil.transAndCheck(mapping, param, null)).collect(Collectors.toList());
                if (params.stream().anyMatch(Objects::isNull)) {
                    continue;
                }
                executableIntegrationInfo.put("params", params);
                executableIntegrationInfo.put("repositoryPath", paramIntegration.getRepositoryPath());
                executableIntegrationInfo.put("method", paramIntegration.getQueryMethod());
            } else if (ParamIntegration.Type.SPECIAL_TECHNOLOGY.equals(paramIntegration.getType())) {
                String routeContent = null;
                if (null != paramIntegration.getFilePath() && !paramIntegration.getFilePath().isEmpty()) {
                    routeContent = FileUtils.fileReader(paramIntegration.getFilePath(), List.of("xml", "dmn", "groovy", "jslt"));
                } else if (null != paramIntegration.getContentPath() && !paramIntegration.getContentPath().isEmpty()) {
                    routeContent = (String) JsonUtil.readPath(param, paramIntegration.getContentPath());
                }
                executableIntegrationInfo.put("logic", routeContent); // 路由内容为null时，会根据technologyType执行内置路由
                executableIntegrationInfo.put("params", value.get());
                executableIntegrationInfo.put("technologyType", paramIntegration.getTechnologyType());
                if (TechnologyType.ROUTE.equals(paramIntegration.getTechnologyType())) {
                    String routeName = findRouteName(routeContent);
                    executableIntegrationInfo.put("routeName", routeName);
                }
            } else if (ParamIntegration.Type.GENERAL_TECHNOLOGY.equals(paramIntegration.getType())) {
                String routeContent = null;
                if (null != paramIntegration.getFilePath() && !paramIntegration.getFilePath().isEmpty()) {
                    routeContent = FileUtils.fileReader(paramIntegration.getFilePath(), List.of("xml"));
                } else if (null != paramIntegration.getContentPath() && !paramIntegration.getContentPath().isEmpty()) {
                    routeContent = (String) JsonUtil.readPath(param, paramIntegration.getContentPath());
                }
                if (null == routeContent) {
                    continue;
                }
                executableIntegrationInfo.put("logic", routeContent);
                executableIntegrationInfo.put("params", value.get());
                String routeName = findRouteName(routeContent);
                executableIntegrationInfo.put("routeName", routeName);
            }
            executableIntegrationInfo.put("name", paramIntegration.getConcept());
            executableIntegrationInfo.put("type", paramIntegration.getType());
        } while (null == executableIntegrationInfo.get("name"));
        log.info("衍生可集成：{}，参数：{}", executableIntegrationInfo.get("name"), executableIntegrationInfo.get("params"));
        return executableIntegrationInfo;
    }

    private static String findRouteName(String routeContent) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        if (null == routeContent) {
            return null;
        }
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        XPath xPath = XPathFactory.newInstance().newXPath();
        Document document = db.parse(new ByteArrayInputStream(routeContent.getBytes()));
        String routeName = ((Node) xPath.evaluate("/routes/route[1]/from/@uri", document, XPathConstants.NODE)).getTextContent();
        return routeName;
    }

    /**
     * 执行查询
     *
     * @return
     */
    public static Object executeQuery(Exchange exchange, Map<String, Object> body) {
        try {
            Class<?> repositoryClass = Class.forName((String) body.get("repositoryPath"));
            ParameterizedType genericInterface = (ParameterizedType) (repositoryClass.getGenericInterfaces()[0]);
            Type[] actualTypeArguments = genericInterface.getActualTypeArguments();
            String entityPath = actualTypeArguments[0].getTypeName();
            String idPath = actualTypeArguments[1].getTypeName();
            List<Object> params = ClassCastUtils.castArrayList(body.get("params"), Object.class);
            String repositoryName = StringUtil.lowerStringFirst(repositoryClass.getSimpleName());
            Object repository = exchange.getContext().getRegistry().lookupByName(repositoryName);
            Method method = Arrays.stream(repositoryClass.getMethods())
                    .filter(m -> m.getName().equals(body.get("method")) && m.getParameterCount() == params.size())
                    .findFirst().orElseThrow();
            Iterator<Type> types = Arrays.stream(method.getGenericParameterTypes()).iterator();
            Iterator<Object> parameterValues = params.iterator();
            // 解析查询参数
            ArrayList<Object> parameters = new ArrayList<>();
            while (types.hasNext()) {
                Type type = types.next();
                String typeName = getTruthName(type.getTypeName(), entityPath, idPath);
                String actualTypeName = "java.lang.Object";
                if (type instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) type;
                    typeName = parameterizedType.getRawType().getTypeName();
                    actualTypeName = getTruthName(parameterizedType.getActualTypeArguments()[0].getTypeName(), entityPath, idPath);
                }
                String value = (String) parameterValues.next();
                if ("org.springframework.data.domain.Pageable".equals(typeName)) {
                    Object page = JsonUtil.readPath(value, "/page");
                    Object size = JsonUtil.readPath(value, "/size");
                    PageRequest pageRequest = PageRequest.of((int) page - 1, (int) size);
                    Sort sort = Sort.unsorted();
                    Object sorts = JsonUtil.readPath(value, "/sort");
                    if (sorts != null) {
                        List<Map<String, String>> sorts1 = (List<Map<String, String>>) sorts;
                        for (Map<String, String> map : sorts1) {
                            String key = map.keySet().iterator().next();
                            sort = sort.and(Sort.by(Sort.Direction.valueOf(map.get(key)), key));
                        }
                    }
                    pageRequest = pageRequest.withSort(sort);
                    parameters.add(pageRequest);
                } else if ("org.springframework.data.domain.Example".equals(typeName)) {
                    Object entity = JsonUtil.writeValueAsObject(value, Class.forName(actualTypeName));
                    Example<?> example = Example.of(entity);
                    parameters.add(example);
                } else if ("java.util.List".equals(typeName) || "java.lang.Iterable".equals(typeName)) {
                    parameters.add(JsonUtil.writeValueAsList(value, Class.forName(actualTypeName)));
                } else {
                    parameters.add(JsonUtil.writeValueAsObject(value, Class.forName(typeName)));
                }
            }
            return method.invoke(repository, parameters.toArray());
        } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getTruthName(String name, String entityPath, String idPath) {
        if ("I".equals(name)) {
            return idPath;
        } else if ("S".equals(name)) {
            return entityPath;
        } else {
            return name;
        }
    }
}