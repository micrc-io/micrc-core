package io.micrc.core.application.businesses;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.CommandParamIntegration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.LogicIntegration;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.LogicRequest;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.support.ExpressionAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
        // 拦截器检查commandJson里边有错误信息时直接处理并终止路由
        intercept()
                .when(exchange -> JsonUtil.readPath((String) exchange.getProperties().get("commandJson"), "/error/errorCode") != null)
                .to("direct://commandAdapterResult") // 适配器层处理
                .marshal().json().convertBodyTo(String.class) // REST返回
                .stop();

        routeTemplate(ROUTE_TMPL_BUSINESSES_SERVICE)
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("logicName", null, "the logicName")
                .templateParameter("commandParamIntegrationsJson", null, "the command integration params")
                .templateParameter("aggregationName", null, "the aggregation name")
                .templateParameter("repositoryName", null, "the repositoryName name")
                .templateParameter("aggregationPath", null, "the aggregation full path")
                .templateParameter("logicName", null, "the logicName")
                .templateParameter("logicIntegrationJson", null, "the logic integration params")
                .templateParameter("embeddedIdentityFullClassName", null, "embedded identity full class name")
                .templateParameter("timePathsJson", null, "time path list json")
                .from("businesses:{{serviceName}}")
                .transacted()
                // 0 暂存入参
                .marshal().json().convertBodyTo(String.class)
                .setProperty("commandJson", body())
                // 1 分发集成
                .setProperty("repositoryName", simple("{{repositoryName}}"))
                .setProperty("embeddedIdentityFullClassName", simple("{{embeddedIdentityFullClassName}}"))
                .setProperty("commandParamIntegrationsJson", simple("{{commandParamIntegrationsJson}}"))
                .dynamicRouter(method(IntegrationCommandParams.class, "integrate"))
                // 2 复制source到target
                .setBody(exchangeProperty("commandJson"))
                .setHeader("pointer", constant("/source"))
                .to("json-patch://select")
                .bean(JsonUtil.class, "writeValueAsString")
                .setHeader("path", constant("/target"))
                .setHeader("value", body())
                .setBody(exchangeProperty("commandJson"))
                .to("json-patch://patch")
                .setProperty("commandJson", body())
                // 2 执行逻辑
                .setProperty("logicName", simple("{{logicName}}"))
                .setProperty("timePathsJson", simple("{{timePathsJson}}"))
                .setProperty("logicIntegrationJson").groovy("new String(java.util.Base64.getDecoder().decode('{{logicIntegrationJson}}'))")
                .setBody(exchangeProperty("commandJson"))
                .to("logic://logic-execute")
                // TODO 仓库集成抽进repository路由内部
                .setBody(exchangeProperty("commandJson"))
                .setHeader("pointer", constant("/target"))
                .to("json-patch://select")
                .marshal().json().convertBodyTo(String.class)
                .setHeader("CamelJacksonUnmarshalType").simple("{{aggregationPath}}")
                .to("dataformat:jackson:unmarshal?allow-unmarshall-type=true")
                .removeHeader("pointer")
                .toD("bean://${exchange.properties.get(repositoryName)}?method=save")
                // TODO 仓库集成抽进repository路由内部 END
                // 3 消息存储
                .setBody(exchangeProperty("commandJson"))
                .setProperty("batchPropertyPath", simple("{{batchPropertyPath}}"))
                .choice()
                .when(constant("").isEqualTo(simple("${exchange.properties.get(batchPropertyPath)}")))
                    .to("eventstore://store")
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
                .end()
                .setBody(exchangeProperty("commandJson"))
                .end();

        from("direct://integration-params")
                // 得到需要集成的集成参数
                .bean(IntegrationCommandParams.class,
                        "executableIntegrationInfo(${exchange.properties.get(unIntegrateParams)}, ${exchange.properties.get(commandJson)})")
                .setProperty("currentIntegrateParam", body())
                .choice()
                .when(constant("").isEqualTo(simple("${exchange.properties.get(currentIntegrateParam).get(protocol)}")))
                    .setBody(simple("${in.body.get(integrateParams).get(id)}"))
                    .marshal().json().convertBodyTo(String.class)
                    .setHeader("CamelJacksonUnmarshalType").simple("${exchange.properties.get(embeddedIdentityFullClassName)}")
                    .to("dataformat:jackson:unmarshal?allow-unmarshall-type=true")
                    .toD("bean://${exchange.properties.get(repositoryName)}?method=findById")
                .otherwise()
                    .setBody(simple("${in.body.get(integrateParams)}"))
                    .setHeader("protocolFilePath", simple("${exchange.properties.get(currentIntegrateParam).get(protocol)}"))
                    .to("req://integration")
                .end()
                .bean(IntegrationCommandParams.class, "processResult")
                .end();

        from("logic://logic-execute")
                // 2.1 执行前置校验
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/before"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(ResultCheck.class, "check(${body}, ${exchange})")
                // TODO 逻辑检查异常是否存在及回滚事务
                // 2.2 执行逻辑
                .setBody(exchangeProperty("logicIntegrationJson"))
                .unmarshal().json(LogicIntegration.class)
                .bean(LogicInParamsResolve.class, "toLogicParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(timePathsJson)})")
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/logic"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(LogicInParamsResolve.class,
                        "toTargetParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(logicIntegrationJson)}, ${exchange.properties.get(timePathsJson)})")
                .setProperty("commandJson", body())
                // 2.3 执行后置校验
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/after"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(ResultCheck.class, "check(${body}, ${exchange})")
                // TODO 逻辑检查异常是否存在及回滚事务
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
         * 聚合名称 - 内部获取
         */
        protected String aggregationName;

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
    }

    private static String patch(String original, String path, String value) {
        String patchCommand = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

        try {
            String pathReplaced = patchCommand.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new RuntimeException("patch fail... please check object...");
        }
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
         * 参数所在对象图(Patch回去用,以/分割) - 注解输入
         */
        private String objectTreePath;

        /**
         * 参数映射
         */
        private Map<String, String> paramMappings;

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
        private Map<String, String> outMappings;

        /**
         * 入集成映射(返回时转换映射)-转Target的 以target为根端点 PATCH
         */
        private Map<String, String> enterMappings;
    }

    public static class TargetSourceClone {
        // TODO 考虑仓库集成于衍生集成混杂的情况

        public static final String sourcePatchString = "[{ \"op\": \"replace\", \"path\": \"/source\", \"value\": {{value}} }]";

        public static final String targetPatchString = "[{ \"op\": \"replace\", \"path\": \"/target\", \"value\": {{value}} }]";

        public static String clone(Object source, String commandJson) {
            try {
                String sourceReplacedJson = sourcePatchString.replace("{{value}}",
                        JsonUtil.writeValueAsStringRetainNull(source));
                String targetReplacedJson = targetPatchString.replace("{{value}}",
                        JsonUtil.writeValueAsStringRetainNull(source));
                // 先用jsonPatch更新指令
                JsonPatch sourcePatch = JsonPatch.fromJson(JsonUtil.readTree(sourceReplacedJson));
                JsonNode sourceReplacedApply = sourcePatch.apply(JsonUtil.readTree(commandJson));
                JsonPatch targetPatch = JsonPatch.fromJson(JsonUtil.readTree(targetReplacedJson));
                JsonNode targetReplacedApply = targetPatch.apply(sourceReplacedApply);
                return JsonUtil.writeValueAsStringRetainNull(targetReplacedApply);
            } catch (IOException | JsonPatchException e) {
                throw new RuntimeException("patch to source failed, please check Target is inited?...");
            }
        }
    }

    public static class ResultCheck {
        public static void check(HashMap<String, Object> checkResult, Exchange exchange) {
            ErrorInfo errorInfo = new ErrorInfo();
            if (null == checkResult.get("checkResult")) {
                errorInfo.setErrorCode("unknown");
            }
            if (!(Boolean) checkResult.get("checkResult")) {
                errorInfo.setErrorCode((String) checkResult.get("errorCode"));
            }
            if (null != errorInfo.getErrorCode()) {
                String commandJson = patch((String) exchange.getProperties().get("commandJson"),
                        "/error",
                        JsonUtil.writeValueAsString(errorInfo));
                exchange.getProperties().put("commandJson", commandJson);
                exchange.getIn().setBody(commandJson);
            }
        }
    }
}

/**
 * 逻辑执行参数处理
 */
class LogicInParamsResolve {

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    public String toLogicParams(LogicIntegration logicIntegration, String commandJson, String timePathsJson) {
        List<String[]> timePathSplitList = JsonUtil.writeValueAsList(timePathsJson, String.class)
                .stream().map(i -> i.split("/")).collect(Collectors.toList());
        Map<String, Object> logicParams = new HashMap<>();
        logicIntegration.getOutMappings().forEach((key, path) -> {
            // 原始结果
            String outMapping = JsonUtil.readTree(commandJson).at(path).toString();

            String[] split = path.split("/");
            List<String[]> lengthEnoughList = timePathSplitList.stream().filter(tps -> tps.length >= split.length)
                    .collect(Collectors.toList());
            for (int i = 0; i < lengthEnoughList.size(); i++) {
                List<String> other = matchGetOtherPath(split, lengthEnoughList, i);
                if (other == null) {
                    continue;
                }
                if (other.isEmpty()) {
                    outMapping = JsonUtil.writeValueAsString(transTime2String(outMapping));
                } else {
                    outMapping = replaceTime(outMapping, other, String.class);
                }
            }
            Object value = JsonUtil.writeValueAsObject(outMapping, Object.class);
            if (null == value) {
                throw new RuntimeException(path + " - the params can`t get value, please check the annotation.like integration annotation error or toLogicMappings annotation have error ");
            }
            logicParams.put(key, value);
        });
        String param = JsonUtil.writeValueAsStringRetainNull(logicParams);
        System.out.println("请求参数：" + param);
        return param;
    }

    /**
     * 替换所有符合时间路径的节点
     *
     * @param outMapping
     * @param other
     * @param timeClass
     * @return
     */
    private String replaceTime(String outMapping, List<String> other, Class<?> timeClass) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iterator = other.iterator();
        boolean isList = false;
        boolean isMap = false;
        while (iterator.hasNext()) {
            String next = iterator.next();
            iterator.remove();
            isList = "#".equals(next);
            isMap = "*".equals(next);
            if (isList || isMap) {
                break;
            }
            builder.append("/").append(next);
        }

        String current = JsonUtil.writeValueAsString(JsonUtil.readPath(outMapping, builder.toString()));

        if (isList) {
            List<Object> list = JsonUtil.writeValueAsList(current, Object.class);
            list = list.stream().map(o -> {
                if (!other.isEmpty()) {
                    return JsonUtil.writeValueAsObject(replaceTime(JsonUtil.writeValueAsString(o), other, timeClass), Object.class);
                }
                return o;
            }).collect(Collectors.toList());
            outMapping = patch(outMapping, builder.toString(), JsonUtil.writeValueAsString(list));
        } else if (isMap) {
            Map<String, Object> map = ClassCastUtils.castHashMap(JsonUtil.writeValueAsObject(current, Object.class), String.class, Object.class);
            map.forEach((k, v) -> {
                if (!other.isEmpty()) {
                    map.put(k, JsonUtil.writeValueAsObject(replaceTime(JsonUtil.writeValueAsString(v), other, timeClass), Object.class));
                }
            });
            outMapping = patch(outMapping, builder.toString(), JsonUtil.writeValueAsString(map));
        } else {
            Object time = JsonUtil.readPath(outMapping, builder.toString());
            String timeResult = null;
            // 判断目标时间格式
            if (timeClass.equals(String.class)) {
                timeResult = JsonUtil.writeValueAsString(transTime2String(time));
            } else {
                timeResult = transTime2Long(time.toString()).toString();
            }
            outMapping = patch(outMapping, builder.toString(), timeResult);
        }
        return outMapping;
    }

    private static String transTime2String(Object o) {
        Instant instant = Instant.ofEpochMilli(Long.parseLong(o.toString()));
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));
        return zonedDateTime.format(dateTimeFormatter);
    }

    private static Long transTime2Long(String o) {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(o, dateTimeFormatter);
        Instant instant = zonedDateTime.toInstant();
        return instant.toEpochMilli();
    }

    public String toTargetParams(Map<String, Object> logicResult, String commandJson, String logicIntegrationJson, String timePathsJson) {
        List<String[]> timePathSplitList = JsonUtil.writeValueAsList(timePathsJson, String.class)
                .stream().map(i -> i.split("/")).collect(Collectors.toList());
        LogicIntegration logicIntegration = JsonUtil.writeValueAsObject(logicIntegrationJson, LogicIntegration.class);
        for (String key : logicResult.keySet()) {
            Object value = logicResult.get(key);
            // 当值序列化后为对象的时候
            if (value instanceof HashMap) {
                Map<String, Object> valueMap = ClassCastUtils.castHashMap(value, String.class, Object.class);
                logicResult.put(key, valueMap);
                valueMap.keySet().forEach(innerKey -> {
                    valueMap.put(innerKey, valueMap.get(innerKey));
                });
            }
            // TODO 当值序列化后为List的时候
            logicResult.put(key, logicResult.get(key));
            String path = logicIntegration.getEnterMappings().get(key);
            if (null == path) {
                continue;
            }
            String logicValue = JsonUtil.writeValueAsStringRetainNull(logicResult.get(key));

            String[] split = path.split("/");
            List<String[]> lengthEnoughList = timePathSplitList.stream().filter(tps -> tps.length >= split.length)
                    .collect(Collectors.toList());
            for (int i = 0; i < lengthEnoughList.size(); i++) {
                List<String> other = matchGetOtherPath(split, lengthEnoughList, i);
                if (other == null) {
                    continue;
                }
                if (other.isEmpty()) {
                    logicValue = JsonUtil.writeValueAsString(transTime2Long(logicValue));
                } else {
                    long l = System.currentTimeMillis();
                    logicValue = replaceTime(logicValue, other, Long.class);
                    System.out.println("用时: " + (System.currentTimeMillis() - l));
                }
            }
            commandJson = patch(commandJson, path, logicValue);
        }
        System.out.println("响应参数：" + commandJson);
        return commandJson;
    }

    /**
     * 匹配路径并获取到剩余的内部路径
     *
     * @param split
     * @param lengthEnoughList
     * @param i
     * @return
     */
    private List<String> matchGetOtherPath(String[] split, List<String[]> lengthEnoughList, int i) {
        String[] tps = lengthEnoughList.get(i);
        boolean match = true;
        for (int j = 0; j < split.length; j++) {
            if (!tps[j].equals(split[j])) {
                match = false;
                break;
            }
        }
        if (!match) {
            return null;
        }
        List<String> other = new ArrayList<>(Arrays.asList(tps).subList(split.length, tps.length));
        return other;
    }

    private String patch(String original, String path, String value) {
        String patchCommand = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

        try {
            String pathReplaced = patchCommand.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new RuntimeException("patch fail... please check object...");
        }
    }
}

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
        Map<String, Object> unIntegrateParams = ClassCastUtils.castHashMap(
                properties.get("unIntegrateParams"), String.class, Object.class);
        properties.put("unIntegrateParams", unIntegrateParams);
        if (unIntegrateParams.size() == 0) {
            // 清除中间变量
            properties.remove("currentIntegrateParam");
            properties.remove("unIntegrateParams");
            properties.remove("commandParamIntegrationsJson");
            properties.remove("commandParamIntegrations");
            return null;
        }
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
            body = JsonUtil.readPath((String) body, "/data");
        }
        commandJson = patch(commandJson, unIntegrateParams.get(name).getObjectTreePath(), JsonUtil.writeValueAsString(body));
        exchange.getProperties().put("commandJson", commandJson);
        unIntegrateParams.remove(name);
        exchange.getProperties().put("currentIntegrateParam", current);
        exchange.getProperties().put("unIntegrateParams", unIntegrateParams);
    }

    private static String patch(String original, String path, String value) {
        String patchCommand = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

        try {
            String pathReplaced = patchCommand.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new RuntimeException("patch fail... please check object...");
        }
    }

    /**
     * 得到一个可执行的集成的集成信息
     *
     * @param unIntegrateParams
     * @return
     */
    public static Map<String, Object> executableIntegrationInfo(Map<String, CommandParamIntegration> unIntegrateParams,
                                                                String commandJson) {
        Map<String, Object> executableIntegrationInfo = new HashMap<>();
        Integer checkNumber = -1;
        for (String key : unIntegrateParams.keySet()) {
            checkNumber++;
            if (checkNumber >= unIntegrateParams.size()) {
                throw new RuntimeException(
                        "the integration file have error, command need integrate, but the param can not use... ");
            }
            CommandParamIntegration commandParamIntegration = unIntegrateParams.get(key);
            LinkedHashMap<String, Object> paramMap = new LinkedHashMap<>();

            // 获取当前查询的每个参数
            commandParamIntegration.getParamMappings().forEach((name, path) -> {
                paramMap.put(name, JsonUtil.readPath(commandJson, path));
            });
            // 检查当前查询是否可执行
            if (paramMap.values().stream().anyMatch(Objects::isNull)) {
                continue;
            }
            if (!"".equals(commandParamIntegration.getProtocol())) {
                String protocolContent = fileReader(commandParamIntegration.getProtocol());
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
            executableIntegrationInfo.put("integrateParams", paramMap);
            break;
        }
        return executableIntegrationInfo;
    }

    /**
     * 读取文件
     *
     * @param filePath
     * @return
     */
    private static String fileReader(String filePath) {
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
}
