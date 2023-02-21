package io.micrc.core.application.derivations;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.LogicRequest;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JsonUtil;
import io.micrc.lib.TimeReplaceUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
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
                // 设置模板参数
                .templateParameter("serviceName", null, "the derivations service name")
                .templateParameter("paramIntegrationsJson", null, "the command integration params")
                .templateParameter("assembler", null, "assembler")
                .templateParameter("timePathsJson", null, "time path list json")
                // 指定service名称为入口端点
                .from("derivations:{{serviceName}}")
                .setProperty("paramIntegrationsJson", simple("{{paramIntegrationsJson}}"))
                .setProperty("param", simple("${in.body}")) // 存储入参到交换区，动态处理及结果汇编需要
                // 处理时间路径
                .setBody(simple("{{timePathsJson}}"))
                .process(exchange -> {
                    exchange.getIn().setBody(JsonUtil.writeValueAsList(exchange.getIn().getBody(String.class), String.class)
                            .stream().map(i -> i.split("/")).collect(Collectors.toList()));
                })
                .setProperty("timePaths", body())
                // 动态路由解析
                .setBody(simple("${exchange.properties.get(param)}"))
                .dynamicRouter(method(IntegrationParams.class, "dynamicIntegrate"))
                // 汇编结果处理
                .setBody(simple("${exchange.properties.get(param)}"))
                // .to("jslt://{{assembler}}")
                .setHeader("mappingFilePath", simple("{{assembler}}"))
                .to("json-mapping://file")
                // 出口
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
                    .toD("dynamic-executor://execute")
                .when(constant("SPECIAL_TECHNOLOGY").isEqualTo(simple("${exchange.properties.get(current).get(type)}")))
                    .setBody(simple("${exchange.properties.get(current).get(params)}"))
                    .toD("${exchange.properties.get(current).get(logic)}")
                .otherwise()
                    .setBody(simple("${exchange.properties.get(current).get(params)}"))
                    .setHeader("logic", simple("${exchange.properties.get(current).get(logic)}"))
                    .bean(LogicRequest.class, "request")
                .endChoice()
                .end()
                  // 处理返回
                .bean(IntegrationParams.class, "processResult");
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
            body = ((Optional<?>) body).orElseThrow();
        } else if (ParamIntegration.Type.OPERATE.equals(currentIntegrateType)
                || ParamIntegration.Type.GENERAL_TECHNOLOGY.equals(currentIntegrateType)
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
        if (ParamIntegration.Type.OPERATE.equals(currentIntegrateType)) {
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
            LinkedHashMap<String, Object> paramMap = new LinkedHashMap<>();
            // 获取当前查询的每个参数
            paramIntegration.getQueryParams().forEach((name, path) -> {
                Object value = JsonUtil.readPath(param, path);
                // 需要执行DMN的时候，时间格式需要转换
                if (ParamIntegration.Type.OPERATE.equals(paramIntegration.getType())) {
                    String valueString = JsonUtil.writeValueAsString(value);
                    valueString = TimeReplaceUtil.matchTimePathAndReplaceTime(timePathList, path, valueString, String.class);
                    value = JsonUtil.writeValueAsObject(valueString, Object.class);
                }
                paramMap.put(name, value);
            });
            // 检查当前查询是否可执行
            if (paramMap.values().stream().anyMatch(Objects::isNull)) {
                continue;
            }
            if (ParamIntegration.Type.QUERY.equals(paramIntegration.getType())) {
                executableIntegrationInfo.put("aggregation", paramIntegration.getAggregation());
                executableIntegrationInfo.put("method", paramIntegration.getQueryMethod());
                executableIntegrationInfo.put("sorts", paramIntegration.getSortParams());
                executableIntegrationInfo.put("pageSizePath", paramIntegration.getPageSizePath());
                executableIntegrationInfo.put("pageNumberPath", paramIntegration.getPageNumberPath());
                executableIntegrationInfo.put("params", paramMap);
            } else if (ParamIntegration.Type.OPERATE.equals(paramIntegration.getType())) {
                executableIntegrationInfo.put("logic", paramIntegration.getLogicName());
                executableIntegrationInfo.put("params", JsonUtil.writeValueAsString(paramMap));
            } else if (ParamIntegration.Type.SPECIAL_TECHNOLOGY.equals(paramIntegration.getType())) {
                executableIntegrationInfo.put("logic", paramIntegration.getLogicName());
                executableIntegrationInfo.put("params", JsonUtil.writeValueAsString(paramMap));
            } else if (ParamIntegration.Type.GENERAL_TECHNOLOGY.equals(paramIntegration.getType())) {
                String routeContent = null;
                if (null != paramIntegration.getFilePath() && !paramIntegration.getFilePath().isEmpty()) {
                    routeContent = fileReader(paramIntegration.getFilePath());
                } else if (null != paramIntegration.getLogicName() && !paramIntegration.getLogicName().isEmpty()) {
                    routeContent = (String) JsonUtil.readPath(param, paramIntegration.getLogicName());
                }
                if (null == routeContent) {
                    continue;
                }
                executableIntegrationInfo.put("logic", routeContent);
                DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                XPath xPath = XPathFactory.newInstance().newXPath();
                Document document = db.parse(new ByteArrayInputStream(routeContent.getBytes()));
                String routeName = ((Node) xPath.evaluate("/routes/route[1]/from/@uri", document, XPathConstants.NODE)).getTextContent();
                executableIntegrationInfo.put("routeName", routeName);
                executableIntegrationInfo.put("params", JsonUtil.writeValueAsString(paramMap));
            }
            executableIntegrationInfo.put("name", paramIntegration.getConcept());
            executableIntegrationInfo.put("type", paramIntegration.getType());
        } while (null == executableIntegrationInfo.get("name"));
        log.info("衍生可集成：{}，参数：{}", executableIntegrationInfo.get("name"), executableIntegrationInfo.get("params"));
        return executableIntegrationInfo;
    }

    /**
     * 读取文件
     *
     * @param filePath
     * @return
     */
    private static String fileReader(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.endsWith(".xml")) {
            throw new RuntimeException("the route file invalid...");
        }
        StringBuffer fileContent = new StringBuffer();
        try {
            InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(filePath);
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String str = null;
            while ((str = in.readLine()) != null) {
                fileContent.append(str);
            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(filePath + " file not found or can`t resolve...");
        }
        return fileContent.toString();
    }

    /**
     * 执行查询
     *
     * @return
     */
    public static Object executeQuery(Exchange exchange, Map<String, Object> body) {
        Object pageSize = JsonUtil.readPath((String) exchange.getProperty("param"), (String) body.get("pageSizePath"));
        Object pageNumber = JsonUtil.readPath((String) exchange.getProperty("param"), (String) body.get("pageNumberPath"));
        PageRequest pageRequest = PageRequest.of((null == pageNumber ? 1 : (int) pageNumber) - 1, null == pageSize ? 10 : (int) pageSize);
        // 追加排序参数
        Sort sort = Sort.unsorted();
        Map<String, String> sorts = ClassCastUtils.castHashMap(body.get("sorts"), String.class, String.class);
        for (Map.Entry<String, String> next : sorts.entrySet()) {
            sort = sort.and(Sort.by(Sort.Direction.valueOf(next.getValue()), next.getKey()));
        }
        pageRequest = pageRequest.withSort(sort);
        try {
            Object repository = exchange.getContext().getRegistry().lookupByName(body.get("aggregation") + "Repository");
            Method method = Arrays.stream(repository.getClass().getMethods())
                    .filter(m -> m.getName().equals(body.get("method"))).findFirst().orElseThrow();
            Iterator<Class<?>> parameterTypes = Arrays.stream(method.getParameterTypes()).iterator();
            Iterator<Object> parameterValues = (ClassCastUtils.castHashMap(body.get("params"), String.class, Object.class)).values().iterator();
            // 解析查询参数
            ArrayList<Object> parameters = new ArrayList<>();
            while (parameterTypes.hasNext()) {
                String typeName = parameterTypes.next().getName();
                if ("org.springframework.data.domain.Pageable".equals(typeName)) {
                    parameters.add(pageRequest);
                    continue;
                }
                parameters.add(JsonUtil.writeObjectAsObject(parameterValues.next(), Class.forName(typeName)));
            }
            return method.invoke(repository, parameters.toArray());
        } catch (IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}