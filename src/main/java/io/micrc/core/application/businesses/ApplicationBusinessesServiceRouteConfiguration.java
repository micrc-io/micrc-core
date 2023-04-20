package io.micrc.core.application.businesses;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.annotations.application.businesses.LogicType;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.CommandParamIntegration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.LogicIntegration;
import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import io.micrc.core.rpc.LogicRequest;
import io.micrc.lib.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.support.ExpressionAdapter;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用业务服务路由定义和参数bean定义
 * 业务服务具体执行逻辑由模版定义，外部注解通过属性声明逻辑变量，由这些参数和路由模版构造执行路由
 *
 * @author weiguan
 * @date 2022-08-27 21:02
 * @since 0.0.1
 */
public class ApplicationBusinessesServiceRouteConfiguration extends MicrcRouteBuilder {
    /**
     * 路由模版ID
     */
    public static final String ROUTE_TMPL_BUSINESSES_SERVICE = ApplicationBusinessesServiceRouteConfiguration.class
            .getName() + ".businessesService";

    @Override
    public void configureRoute() throws Exception {

        // todo,事务回滚测试
        // DMN检查错误
        onException(IllegalStateException.class)
                .handled(true)
                .to("error-handle://command");

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://business");

        routeTemplate(ROUTE_TMPL_BUSINESSES_SERVICE)
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("logicName", null, "the logic name")
                .templateParameter("logicType", null, "the logic type")
                .templateParameter("logicPath", null, "the logic path")
                .templateParameter("commandParamIntegrationsJson", null, "the command integration params")
                .templateParameter("repositoryName", null, "the repositoryName name")
                .templateParameter("aggregationPath", null, "the aggregation full path")
                .templateParameter("logicIntegrationJson", null, "the logic integration params")
                .templateParameter("embeddedIdentityFullClassName", null, "embedded identity full class name")
                .templateParameter("timePathsJson", null, "time path list json")
                .templateParameter("batchPropertyPath", null, "batch property path")
                .from("businesses:{{serviceName}}")
                .setProperty("repositoryName", simple("{{repositoryName}}"))
                .setProperty("embeddedIdentityFullClassName", simple("{{embeddedIdentityFullClassName}}"))
                .setProperty("commandParamIntegrationsJson", simple("{{commandParamIntegrationsJson}}"))
                .setProperty("logicName", simple("{{logicName}}"))
                .setProperty("logicType", simple("{{logicType}}"))
                .setProperty("logicPath", simple("{{logicPath}}"))
                .setProperty("batchPropertyPath", simple("{{batchPropertyPath}}"))
                .setProperty("aggregationPath", simple("{{aggregationPath}}"))
                .setProperty("logicIntegrationJson").groovy("new String(java.util.Base64.getDecoder().decode('{{logicIntegrationJson}}'))")
                .setProperty("timePathsJson", simple("{{timePathsJson}}"))
                .setProperty("fieldMap", simple("{{fieldMap}}"))
                .transacted()
                // 1.处理请求
                .to("direct://handle-request")
                // 2.动态集成
                .to("direct://dynamic-integration")
                // 3.数据处理
                .to("direct://executor-data")
                // 4.处理结果
                .to("direct://handle-result")
                .end();

        from("direct://executor-data-one")
                // 3.1.复制资源
                .to("direct://copy-source")
                // 3.2.解析时间
                .to("direct://parse-time")
                // 3.3.执行逻辑
                .to("logic://logic-execute")
                // 3.4.存储实体
                .to("direct://save-entity")
                // 3.5.存储消息
                .to("direct://save-message")
                .end();

        from("direct://executor-data")
                .choice()
                .when(simple("${exchange.properties.get(batchName)}").isNull())
                .to("direct://executor-data-one")
                .endChoice()
                .otherwise()
                .setBody(exchangeProperty("batchIntegrateResult"))
                .split(new SplitList()).parallelProcessing()
                .setProperty("integrateResult", body())
                .bean(IntegrationCommandParams.class, "batchIntegrate")
                .to("direct://executor-data-one")
                .endChoice()
                .end();

        from("direct://handle-request")
                .setProperty("command", body())
                .marshal().json().convertBodyTo(String.class)
                .setProperty("commandJson", body());

        from("direct://handle-result")
                .process(exchange -> {
                    String commandJson = (String) exchange.getProperty("commandJson");
                    Object command = exchange.getProperty("command");
                    BeanUtils.copyProperties(JsonUtil.writeValueAsObject(commandJson, command.getClass()), command);
                });

        from("direct://dynamic-integration")
                .dynamicRouter(method(IntegrationCommandParams.class, "integrate"));

        from("direct://parse-time")
                .setBody(exchangeProperty("timePathsJson"))
                .process(exchange -> {
                    List<String[]> timePaths = JsonUtil.writeValueAsList(exchange.getIn().getBody(String.class), String.class)
                            .stream().map(i -> i.split("/")).collect(Collectors.toList());
                    exchange.getIn().setBody(timePaths);
                })
                .setProperty("timePaths", body());

        from("direct://copy-source")
                .setBody(exchangeProperty("commandJson"))
                .setHeader("pointer", constant("/source"))
                .to("json-patch://select")
                .bean(JsonUtil.class, "writeValueAsString")
                .setHeader("path", constant("/target"))
                .setHeader("value", body())
                .setBody(exchangeProperty("commandJson"))
                .to("json-patch://patch")
                .setProperty("commandJson", body());

        from("direct://save-entity")
                .process(exchange -> {
                    String commandJson = (String) exchange.getProperties().get("commandJson");
                    Object id = JsonUtil.readPath(commandJson, "/target/identity/id");
                    Map<String, Object> properties = exchange.getProperties();
                    if (null == id) {
                        commandJson = JsonUtil.add(commandJson, "/target/identity/id", String.valueOf(SnowFlakeIdentity.getInstance().nextId()));
                        properties.put("commandJson", commandJson);
                    }
                    // 查看是否存在级联操作
                    String fieldStr = (String) properties.get("fieldMap");
                    Map<String, String> fieldMap = JsonUtil.writeValueAsObject(fieldStr, Map.class);
                    if (!fieldMap.isEmpty()) {
                        HashMap<String, Object> batchMap = new HashMap<>();
                        HashMap<String, Object> oneMap = new HashMap<>();
                        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
                            String filedName = entry.getKey();
                            String node = JsonUtil.readTree(commandJson).at("/target/" + filedName).toString();
                            if(JsonUtil.hasPath(node, "/identity/id")) {
                                // 多对一
                                Object identity = JsonUtil.readPath(node, "/identity/id");
                                if (identity == null) {
                                    node = JsonUtil.add(node, "/identity/id",
                                            String.valueOf(SnowFlakeIdentity.getInstance().nextId()));
                                }
                                // 将node转换成对应的实体
                                oneMap.put(filedName, node);
                            } else {
                                // 一对多
                                List<Object> list = JsonUtil.writeValueAsList(node, Object.class);
                                for (int i = 0; i < list.size(); i++) {
                                    String entityJson = JsonUtil.writeValueAsString(list.get(i));
                                    Object identity = JsonUtil.readPath(entityJson, "/identity/id");
                                    if (identity == null) {
                                        entityJson = JsonUtil.add(entityJson, "/identity/id", String.valueOf(SnowFlakeIdentity.getInstance().nextId()));
                                    }
                                    list.set(i, entityJson);
                                }
                                // 将node转换成对应的实体
                                batchMap.put(filedName, list.toString());
                            }
                            commandJson = JsonUtil.patch(commandJson, "/target/" + filedName, "null");
                        }
                        properties.put("commandJson", commandJson);
                        properties.put("batchMap", JsonUtil.writeValueAsString(batchMap));
                        properties.put("oneMap", JsonUtil.writeValueAsString(oneMap));
                    }
                })
                .setBody(exchangeProperty("commandJson"))
                .setHeader("pointer", constant("/target"))
                .to("json-patch://select")
                .marshal().json().convertBodyTo(String.class)
                .setHeader("CamelJacksonUnmarshalType").exchangeProperty("aggregationPath")
                .choice()
                // 不存在级联操作
                .when(simple("${exchange.properties.get(fieldMap)}").isEqualTo("{}"))
                .to("dataformat:jackson:unmarshal?allow-unmarshall-type=true")
                .endChoice()
                .otherwise()
                .process(exchange -> {
                    String entityPath = exchange.getIn().getHeader("CamelJacksonUnmarshalType", String.class);
                    Class<?> entityClass = Class.forName(entityPath);
                    String fieldStr = (String) exchange.getProperties().get("fieldMap");
                    Map<String, String> fieldMap = JsonUtil.writeValueAsObject(fieldStr, Map.class);
                    String oneStr = (String) exchange.getProperties().get("oneMap");
                    Map<String, Object> oneMap = JsonUtil.writeValueAsObject(oneStr, Map.class);
                    String batchStr = (String) exchange.getProperties().get("batchMap");
                    Map<String, Object> batchMap = JsonUtil.writeValueAsObject(batchStr, Map.class);
                    Method[] methods = entityClass.getMethods();
                    Object entity = JsonUtil.writeValueAsObject(exchange.getIn().getBody(String.class), entityClass);
                    for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
                        String key = entry.getKey();
                        Class<?> clazz = Class.forName(entry.getValue());
                        Method method = Arrays.stream(methods)
                                .filter(m -> m.getName().startsWith("set") && m.getName().contains(StringUtil.upperStringFirst(key)))
                                .findFirst().orElseThrow();
                        if(batchMap.containsKey(key)) {
                            method.invoke(entity, JsonUtil.writeValueAsList(batchMap.get(key).toString(), clazz));
                        } else {
                            method.invoke(entity, JsonUtil.writeValueAsObject(oneMap.get(key).toString(), clazz));
                        }
                    }
                    exchange.getIn().setBody(entity);
                })
                .endChoice()
                .end()
                .removeHeader("pointer")
                .toD("bean://${exchange.properties.get(repositoryName)}?method=save");

        from("direct://save-message")
                .setBody(exchangeProperty("commandJson"))
                .setHeader("pointer", constant("/event/eventName"))
                .to("json-patch://select")
                .setProperty("eventName", body())
                .setBody(exchangeProperty("commandJson"))
                .choice()
                .when(simple("${exchange.properties.get(eventName)}").isNull())
                // nothing to do
                .endChoice()
                .when(constant("").isEqualTo(simple("${exchange.properties.get(batchPropertyPath)}")))
                .to("eventstore://store")
                .endChoice()
                .otherwise()
                .setHeader("pointer", constant("/event/eventBatchData"))
                .to("json-patch://select")
                .split(new SplitList()).parallelProcessing()
                .bean(JsonUtil.class, "writeValueAsString")
                .setHeader("path", simple("${exchange.properties.get(batchPropertyPath)}"))
                .setHeader("value", body())
                .setBody(exchangeProperty("commandJson"))
                .to("json-patch://add")
                .to("eventstore://store")
                .end()
                .endChoice()
                .end();

        from("direct://integration-params")
                .setBody(exchangeProperty("currentIntegrateParam"))
                .choice()
                .when(constant("").isEqualTo(simple("${exchange.properties.get(currentIntegrateParam).get(protocol)}")))
                .setBody(simple("${in.body.get(integrateParams).get(id)}"))
                .marshal().json().convertBodyTo(String.class)
                .setHeader("CamelJacksonUnmarshalType").simple("${exchange.properties.get(embeddedIdentityFullClassName)}")
                .to("dataformat:jackson:unmarshal?allow-unmarshall-type=true")
                .toD("bean://${exchange.properties.get(repositoryName)}?method=findById")
                .endChoice()
                .otherwise()
                .setBody(simple("${in.body.get(integrateParams)}"))
                .setProperty("protocolFilePath", simple("${exchange.properties.get(currentIntegrateParam).get(protocol)}"))
                .to("req://integration")
                .endChoice()
                .end()
                .bean(IntegrationCommandParams.class, "processResult")
                .end();

        from("logic://logic-execute")
                .routeId("logic://logic-execute")
                .choice()
                .when(exchangeProperty("logicType").isEqualTo("DMN"))
                .to("logic://logic-execute-dmn")
                .when(exchangeProperty("logicType").isEqualTo("GROOVY_SHELL"))
                .to("logic://logic-execute-groovy")
                .endChoice()
                .end();

        from("logic://logic-execute-dmn")
                .routeId("logic://logic-execute-dmn")
                .setBody(exchangeProperty("commandJson"))
                // 2.1 执行前置校验
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/before"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(ResultCheck.class, "check(${body}, ${exchange})")
                // TODO 逻辑检查异常是否存在及回滚事务
                // 2.2 执行逻辑
                .setBody(exchangeProperty("logicIntegrationJson"))
                .unmarshal().json(LogicIntegration.class)
                .bean(LogicInParamsResolve.class, "toLogicParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(timePaths)}, ${exchange.properties.get(logicType)})")
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/logic"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(LogicInParamsResolve.class,
                        "toTargetParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(logicIntegrationJson)}, ${exchange.properties.get(timePaths)}, ${exchange.properties.get(logicType)})")
                .setProperty("commandJson", body())
                // 2.3 执行后置校验
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/after"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(ResultCheck.class, "check(${body}, ${exchange})")
                // TODO 逻辑检查异常是否存在及回滚事务
                .end();


        from("logic://logic-execute-groovy")
                .routeId("logic://logic-execute-groovy")
                // 执行逻辑
                .setBody(exchangeProperty("logicIntegrationJson"))
                .unmarshal().json(LogicIntegration.class)
                .bean(LogicInParamsResolve.class, "toLogicParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(timePaths)}, ${exchange.properties.get(logicType)})")
                .setHeader("groovy", simple("${exchange.properties.get(logicPath)}"))
                .to("dynamic-groovy://execute")
                .unmarshal().json(HashMap.class)
                .bean(LogicInParamsResolve.class,
                        "toTargetParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(logicIntegrationJson)}, ${exchange.properties.get(timePaths)}, ${exchange.properties.get(logicType)})")
                .setProperty("commandJson", body())
                .end();
    }

    /**
     * 对象列表拆分器 -- TODO 通用 -- 抽走
     */
    public class SplitList extends ExpressionAdapter {
        @Override
        public Object evaluate(Exchange exchange) {
            @SuppressWarnings("unchecked")
            List<Object> objects = (List<Object>) exchange.getIn().getBody();
            if (null != objects) {
                return objects.iterator();
            } else {
                return null;
            }
        }
    }

    /**
     * 应用业务服务路由参数Bean
     *
     * @author weiguan
     * @date 2022-08-27 23:02
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationBusinessesServiceDefinition extends AbstractRouteTemplateParamDefinition {
        /**
         * 业务服务 - 内部获取
         */
        protected String serviceName;

        /**
         * 批量属性路径，需要批量发送事件时存在
         */
        protected String batchPropertyPath;

        /**
         * 执行逻辑名(截取Command的一部分) - 内部获取
         */
        protected String logicName;

        /**
         * 资源库名称 - 内部获取
         */
        protected String repositoryName;

        /**
         * 聚合全路径名称 - 内部获取
         */
        protected String aggregationPath;

        /**
         * 需集成参数属性定义
         */
        protected String commandParamIntegrationsJson;

        /**
         * 逻辑调用参数定义
         */
        protected String logicIntegrationJson;

        /**
         * 嵌套标识符类名称
         */
        protected String embeddedIdentityFullClassName;

        /**
         * 所有时间路径
         */
        protected String timePathsJson;

        /**
         * 所执行逻辑类型
         */
        protected String logicType;

        /**
         * 如执行脚本,则是提供的脚本路径
         */
        protected String logicPath;

        /**
         * 存在级联操作的字段
         */
        protected String fieldMap;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class CommandParamIntegration {

        /**
         * 属性名称-用来patch回原始CommandJson中 - 内部获取
         */
        private String paramName;

        /**
         * 参数缺失则忽略此次集成
         */
        private boolean ignoreIfParamAbsent;

        private boolean batchFlag;

        /**
         * 请求映射文件
         *
         * @return
         */
        private String requestMapping;

        /**
         * 响应映射文件
         *
         * @return
         */
        private String responseMapping;

        /**
         * openApi集成协议 - 注解输入
         */
        private String protocol;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class LogicIntegration {

        /**
         * 出集成映射(调用时转换映射)
         */
        private Map<String, String> paramMappingMap;

        /**
         * 入集成映射(返回时转换映射)-转Target的 以target为根端点 PATCH
         */
        private Map<String, String> resultMappingMap;
    }

    public static class ResultCheck {
        public static void check(HashMap<String, Object> checkResult, Exchange exchange) {
            String errorCode = null;
            if (null == checkResult.get("checkResult")) {
                errorCode = "System-DMN-error";
            } else if (!(Boolean) checkResult.get("checkResult")) {
                errorCode = (String) checkResult.get("errorCode");
            }
            if (null != errorCode) {
                throw new IllegalStateException(errorCode);
            }
        }
    }
}

/**
 * 逻辑执行参数处理
 */
@Slf4j
class LogicInParamsResolve {

    public String toLogicParams(LogicIntegration logicIntegration, String commandJson, List<String[]> timePathList, String logicType) {
        Map<String, Object> logicParams = new HashMap<>();
        logicIntegration.getParamMappingMap().forEach((key, mapping) -> {
            // 原始结果
            String value = JsonUtil.transAndCheck(mapping, commandJson, null);
            if (null == value) {
                // dmn 入参为null 也需要保留
                logicParams.put(key, null);
                return;
            }
            if (LogicType.DMN.name().equals(logicType)) {
                value = TimeReplaceUtil.matchTimePathAndReplaceTime(timePathList, "", value, String.class);// 根目录路径为""
            }
            logicParams.put(key, JsonUtil.writeValueAsObject(value, Object.class));
        });
        String params = JsonUtil.writeValueAsStringRetainNull(logicParams);
        log.info("业务规则执行条件：{}", params);
        return params;
    }

    public String toTargetParams(Map<String, Object> logicResult, String commandJson, String logicIntegrationJson, List<String[]> timePathList, String logicType) {
        log.info("业务规则执行结果：{}", logicResult);
        Object angle = logicResult.get("angle");
        LogicIntegration logicIntegration = JsonUtil.writeValueAsObject(logicIntegrationJson, LogicIntegration.class);
        String resultJson = JsonUtil.writeValueAsString(logicResult);
        for (Map.Entry<String, String> entry : logicIntegration.getResultMappingMap().entrySet()) {
            String targetPath = entry.getKey();
            // 需要赋值状态执行结果含纬度信息，则状态赋值到对应纬度
            if ("/target/state".equals(targetPath) && null != angle && angle.toString().length() > 0) {
                targetPath = targetPath + "/" + angle;
            }
            String value = JsonUtil.transform(entry.getValue(), resultJson);
            if (null == value) {
                continue;
            }
            // 补全所有目的路径不存在的节点
            commandJson = JsonUtil.supplementNotExistsNode(commandJson, targetPath);
            if (LogicType.DMN.name().equals(logicType)) {
                value = TimeReplaceUtil.matchTimePathAndReplaceTime(timePathList, targetPath, value, Long.class);
            }
            commandJson = JsonUtil.patch(commandJson, targetPath, value);
        }
        return commandJson;
    }
}

@Slf4j
class IntegrationCommandParams {

    public static String integrate(@ExchangeProperties Map<String, Object> properties) {
        // 1.判断是否有需要集成的参数
        List<CommandParamIntegration> commandParamIntegrations = ClassCastUtils.castArrayList(properties
                .get("commandParamIntegrations"), CommandParamIntegration.class);
        // 初始化动态路由集成控制信息
        if (null == commandParamIntegrations) {
            commandParamIntegrations = JsonUtil.writeValueAsList(
                    (String) properties.get("commandParamIntegrationsJson"), CommandParamIntegration.class);
            properties.put("commandParamIntegrations", commandParamIntegrations);
            Map<String, CommandParamIntegration> unIntegrateParams = commandParamIntegrations.stream()
                    .collect(Collectors.toMap(CommandParamIntegration::getParamName, integrate -> integrate));
            properties.put("unIntegrateParams", unIntegrateParams);
        }
        Map<String, CommandParamIntegration> unIntegrateParams = ClassCastUtils.castHashMap(
                properties.get("unIntegrateParams"), String.class, CommandParamIntegration.class);
        properties.put("unIntegrateParams", unIntegrateParams);
        // 存在批量处理的集成，跳出当前循环
        if (properties.containsKey("batchIntegrate")) {
            return null;
        }
        Map<String, Object> currentIntegration = executableIntegrationInfo(unIntegrateParams, (String) properties.get("commandJson"), false);
        if (null == currentIntegration) {
            // 清除中间变量
            properties.remove("currentIntegrateParam");
            properties.remove("unIntegrateParams");
            properties.remove("commandParamIntegrationsJson");
            properties.remove("commandParamIntegrations");
            return null;
        }
        properties.put("currentIntegrateParam", currentIntegration);
        return "direct://integration-params";
    }

    public static String batchIntegrate(@ExchangeProperties Map<String, Object> properties) {
        Object integrateResult = properties.get("integrateResult");
        String commandJson = (String) properties.get("commandJson");
        String batchName = (String) properties.get("batchName");
        Map<String, CommandParamIntegration> batchIntegrate = ClassCastUtils.castHashMap(
                properties.get("batchIntegrate"), String.class, CommandParamIntegration.class);
        properties.put("unIntegrateParams", batchIntegrate);
        properties.remove("batchIntegrate");

        commandJson = JsonUtil.patch(commandJson, "/" + batchName, JsonUtil.writeValueAsString(integrateResult));
        properties.put("commandJson", commandJson);
        Map<String, Object> currentIntegration = executableIntegrationInfo(batchIntegrate, commandJson, false);
        if (null == currentIntegration) {
            // 清除中间变量
            properties.remove("currentIntegrateParam");
            properties.remove("unIntegrateParams");
            properties.remove("commandParamIntegrationsJson");
            properties.remove("commandParamIntegrations");
            return null;
        }
        properties.put("currentIntegrateParam", currentIntegration);
        return "direct://integration-params";
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
        String commandJson = (String) properties.get("commandJson");
        Map<String, Object> current = ClassCastUtils.castHashMap(properties.get("currentIntegrateParam"), String.class, Object.class);
        Map<String, CommandParamIntegration> unIntegrateParams = ClassCastUtils.castHashMap(exchange.getProperty("unIntegrateParams", Map.class), String.class, CommandParamIntegration.class);
        String name = (String) current.get("paramName");
        String protocol = (String) current.get("protocol");
        if ("".equals(protocol)) {
            body = ((Optional<?>) body).orElseThrow();
        } else {
            if (!"200".equals(JsonUtil.readPath((String) body, "/code"))) {
                throw new RuntimeException("Integrate [" + name + "] error: " + JsonUtil.readPath((String) body, "/message"));
            }
            body = JsonUtil.readPath((String) body, "/data");
        }
        String data = JsonUtil.transform(unIntegrateParams.get(name).getResponseMapping(), JsonUtil.writeValueAsString(body));
        log.info("业务已集成：{}，结果：{}", name, data);

        // 判断返回结果是否需要进行批处理
        boolean batchFlag = unIntegrateParams.get(name).isBatchFlag();
        if (batchFlag) {
            unIntegrateParams.remove(name);
            // 记录需要批量处理的集成
            exchange.getProperties().put("batchIntegrate", unIntegrateParams);
            exchange.setProperty("batchName", name);
            List<?> list = JsonUtil.writeValueAsList(data, Class.forName((String) properties.get("aggregationPath")));
            exchange.setProperty("batchIntegrateResult", list);
            return;
        }
        commandJson = JsonUtil.patch(commandJson, "/" + name, data);
        exchange.getProperties().put("commandJson", commandJson);
        unIntegrateParams.remove(name);
        exchange.getProperties().put("currentIntegrateParam", current);
        exchange.getProperties().put("unIntegrateParams", unIntegrateParams);
    }

    /**
     * 得到一个可执行的集成的集成信息
     *
     * @param unIntegrateParams
     * @return
     */
    public static Map<String, Object> executableIntegrationInfo(Map<String, CommandParamIntegration> unIntegrateParams,
                                                                String commandJson, Boolean isBatchExecutor) {
        if (unIntegrateParams.isEmpty()) {
            return null;
        }
        log.info("业务未集成：{}", String.join(",", unIntegrateParams.keySet()));
        Map<String, Object> executableIntegrationInfo = new HashMap<>();
        for (String key : unIntegrateParams.keySet()) {
            CommandParamIntegration commandParamIntegration = unIntegrateParams.get(key);
            // 批处理集成放在后面进行处理
            if (!commandParamIntegration.isBatchFlag() == isBatchExecutor) {
                continue;
            }
            String protocolContent = null;
            if (StringUtils.hasText(commandParamIntegration.getProtocol())) {
                protocolContent = FileUtils.fileReader(commandParamIntegration.getProtocol(), List.of("json"));
            }
            // 获取当前查询的每个参数
            String body = JsonUtil.transAndCheck(commandParamIntegration.getRequestMapping(), commandJson, protocolContent);
            if (null == body) {
                continue;
            }
            if (StringUtils.hasText(commandParamIntegration.getProtocol())) {
                JsonNode protocolNode = JsonUtil.readTree(protocolContent);
                // 收集host
                JsonNode urlNode = protocolNode
                        .at("/servers").get(0)
                        .at("/url");
                executableIntegrationInfo.put("host", urlNode.textValue());
                // 收集operationId
                JsonNode operationNode = protocolNode
                        .at("/paths").elements().next().elements().next()
                        .at("/operationId");
                executableIntegrationInfo.put("operationId", operationNode.textValue());
            }
            // 如果能够集成,收集信息,然后会自动跳出循环
            executableIntegrationInfo.put("paramName", commandParamIntegration.getParamName());
            executableIntegrationInfo.put("protocol", commandParamIntegration.getProtocol());
            executableIntegrationInfo.put("integrateParams", JsonUtil.writeValueAsObject(body, Object.class));
            break;
        }
        if (null == executableIntegrationInfo.get("paramName")) {
            // 是否存在批处理的集成
            if (!isBatchExecutor && unIntegrateParams.values().stream().anyMatch(CommandParamIntegration::isBatchFlag)) {
                return executableIntegrationInfo(unIntegrateParams, commandJson, true);
            }
            // 是否只剩下了可选集成
            if (unIntegrateParams.values().stream().allMatch(CommandParamIntegration::isIgnoreIfParamAbsent)) {
                log.info("业务忽略集成：{}", String.join(",", unIntegrateParams.keySet()));
                return null;
            }
            throw new RuntimeException(
                    "the integration file have error, command need integrate, but the param can not use... ");
        }
        log.info("业务可集成：{}，参数：{}", executableIntegrationInfo.get("paramName"), executableIntegrationInfo.get("integrateParams"));
        return executableIntegrationInfo;
    }
}
