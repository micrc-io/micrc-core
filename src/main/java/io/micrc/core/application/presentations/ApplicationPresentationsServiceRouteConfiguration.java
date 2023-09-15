package io.micrc.core.application.presentations;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.IntegrationsInfo;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JsonUtil;
import io.micrc.lib.StringUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用展示服务路由定义和参数bean定义
 * 展示服务具体执行逻辑由模版定义，外部注解通过属性声明逻辑变量，由这些参数和路由模版构造执行路由
 *
 * @author hyosunghan
 * @date 2022-09-05 11:23
 * @since 0.0.1
 */
public class ApplicationPresentationsServiceRouteConfiguration extends MicrcRouteBuilder {
    // 路由模版ID
    public static final String ROUTE_TMPL_PRESENTATIONS_SERVICE =
            ApplicationPresentationsServiceRouteConfiguration.class.getName() + ".presentationsService";

    /**
     * 配置展示服务路由模板
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

        routeTemplate(ROUTE_TMPL_PRESENTATIONS_SERVICE)
                .templateParameter("serviceName", null, "the presentations service name")
                .templateParameter("paramIntegrationsJson", null, "the command integration params")
                .templateParameter("assembler", null, "assembler")
                .from("presentations:{{serviceName}}")
                .setProperty("paramIntegrationsJson", constant("{{paramIntegrationsJson}}"))
                .setProperty("assembler", constant("{{assembler}}"))
                // 1.处理请求
                .to("direct://handle-request-presentation")
                // 2.动态集成
                .to("direct://dynamic-integration-presentation")
                // 3.处理结果
                .to("direct://handle-result-presentation")
                .end();

        from("direct://handle-request-presentation")
                .setProperty("param", body())
                .setHeader("path", constant("/_param"))
                .setHeader("value", body())
                .setBody(constant("{}"))
                .to("json-patch://add")
                .setProperty("buffer", body())
                .end();

        from("direct://presentations-integration")
                .setBody(exchangeProperty("current"))
                // 构造发送
                .choice()
                .when(constant("QUERY").isEqualTo(simple("${exchange.properties.get(current).get(type)}")))
                    .bean(IntegrationParams.class, "executeQuery")
                .endChoice()
                .otherwise()
                    .setBody(simple("${exchange.properties.get(current).get(params)}"))
                    .setProperty("protocolFilePath", simple("${exchange.properties.get(current).get(protocol)}"))
                    .to("req://integration")
                .endChoice()
                .end()
                  // 处理返回
                .bean(IntegrationParams.class, "processResult");

        from("direct://dynamic-integration-presentation")
                .dynamicRouter(method(IntegrationParams.class, "dynamicIntegrate"));

        from("direct://handle-result-presentation")
                .setBody(exchangeProperty("buffer"))
                .setHeader("mappingContent", exchangeProperty("assembler"))
                .to("json-mapping://content");
    }

    /**
     * 应用展示服务路由参数Bean
     *
     * @author hyosunghan
     * @date 2022-09-05 11:30
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationPresentationsServiceDefinition extends AbstractRouteTemplateParamDefinition {

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
        if (unIntegrateParams.isEmpty()) {
            return null;
        }
        log.info("展示未集成：{}", unIntegrateParams.stream().map(ParamIntegration::getConcept).collect(Collectors.joining(",")));
        Map<String, Object> currentIntegration = findExecutable(unIntegrateParams, getJson(properties));
        if (null == currentIntegration) {
            properties.remove("current");
            properties.remove("unIntegrateParams");
            properties.remove("paramIntegrationsJson");
            properties.remove("paramIntegrations");
            return null;
        }
        properties.put("current", currentIntegration);
        return "direct://presentations-integration";
    }

    private static String getJson(Map<String, Object> properties) {
        Object current = properties.get("current");
        String json;
        if (null == current) {
            // 首次集成可直接取入参
            json = (String) properties.get("param");
        } else {
            // 后续集成需从缓冲区取
            json = (String) properties.get("buffer");
        }
        return json;
    }

    /**
     * 处理当前集成结果
     *
     * @param exchange
     */
    public static void processResult(Exchange exchange) throws Exception {
        Map<String, Object> properties = exchange.getProperties();
        Object body = exchange.getIn().getBody();
        if (body instanceof byte[]) {
            body = new String((byte[]) body);
        }
        String buffer = (String) properties.get("buffer");
        Map<String, Object> current = ClassCastUtils.castHashMap(properties.get("current"), String.class, Object.class);
        String name = (String) current.get("name");
        ParamIntegration.Type currentIntegrateType = (ParamIntegration.Type) current.get("type");
        if (ParamIntegration.Type.QUERY.equals(currentIntegrateType) && body instanceof Optional) {
            body = ((Optional<?>) body).orElse(null);
        } else if (ParamIntegration.Type.INTEGRATE.equals(currentIntegrateType)) {
            body = JsonUtil.readPath((String) body, "/data");
        }
        String responseMapping = (String) current.get("responseMapping");
        String data = JsonUtil.transform(responseMapping, JsonUtil.writeValueAsString(body));
        log.info("展示已集成：{}，结果：{}", name, data);
        List<ParamIntegration> paramIntegrations = ClassCastUtils.castArrayList(exchange.getProperties().get("paramIntegrations"), ParamIntegration.class);
        // 将上次执行的结果放回至原有属性集成参数之中
        ParamIntegration find = paramIntegrations.stream()
                .filter(paramIntegration -> name.equals(paramIntegration.getConcept()))
                .findFirst().orElseThrow();
        // 标识该参数已成功
        find.setIntegrationComplete(true);
        buffer = JsonUtil.add(buffer, "/" + find.getConcept(), data);
        exchange.getProperties().put("buffer", buffer);
        exchange.getProperties().put("paramIntegrations", paramIntegrations);
    }

    /**
     * 得到一个可执行的集成的集成信息
     *
     * @param unIntegrateParams
     * @param json
     * @return
     */
    public static Map<String, Object> findExecutable(List<ParamIntegration> unIntegrateParams, String json) {
        Map<String, Object> executableIntegrationInfo = new HashMap<>();
        for (ParamIntegration paramIntegration : unIntegrateParams) {
            // 获取当前查询的每个参数
            String body;
            if (ParamIntegration.Type.QUERY.equals(paramIntegration.getType())) {
                List<String> params = paramIntegration.getParamMappings().stream()
                        .map(mapping -> JsonUtil.transAndCheck(mapping, json, null)).collect(Collectors.toList());
                if (params.stream().anyMatch(Objects::isNull)) {
                    continue;
                }
                executableIntegrationInfo.put("params", params);
                // 如果能够集成,收集信息,然后会自动跳出循环
                executableIntegrationInfo.put("repositoryPath", paramIntegration.getRepositoryPath());
                executableIntegrationInfo.put("method", paramIntegration.getQueryMethod());
            } else if (ParamIntegration.Type.INTEGRATE.equals(paramIntegration.getType())) {
                String protocolContent = IntegrationsInfo.get(paramIntegration.getProtocol()).getProtocolContent();
                body = JsonUtil.transAndCheck(paramIntegration.getRequestMapping(), json, protocolContent);
                if (null == body) {
                    continue;
                }
                // 如果能够集成,收集信息,然后会自动跳出循环
                executableIntegrationInfo.put("protocol", paramIntegration.getProtocol());
                executableIntegrationInfo.put("params", JsonUtil.writeValueAsObject(body, Object.class));
            }
            executableIntegrationInfo.put("responseMapping", paramIntegration.getResponseMapping());
            executableIntegrationInfo.put("name", paramIntegration.getConcept());
            executableIntegrationInfo.put("type", paramIntegration.getType());
            break;
        }
        if (null != executableIntegrationInfo.get("name")) {
            log.info("展示可集成：{}，参数：{}", executableIntegrationInfo.get("name"), JsonUtil.writeValueAsString(executableIntegrationInfo.get("params")));
            return executableIntegrationInfo;
        }
        // 是否只剩下了可选集成
        if (unIntegrateParams.stream().allMatch(ParamIntegration::isIgnoreIfParamAbsent)) {
            log.info("展示不集成：{}", unIntegrateParams.stream().map(ParamIntegration::getConcept).collect(Collectors.joining(",")));
            return null;
        }
        throw new RuntimeException("the integration file have error, command need integrate, but the param can not use... ");
    }

    /**
     * 执行查询
     *
     * @return
     */
    public static Object executeQuery(Exchange exchange) {
        try {
            HashMap<String, Object> body = exchange.getIn().getBody(HashMap.class);
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