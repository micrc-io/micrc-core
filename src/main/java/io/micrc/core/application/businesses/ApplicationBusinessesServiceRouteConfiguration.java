package io.micrc.core.application.businesses;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.CommandParamIntegration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.LogicIntegration;
import io.micrc.core.framework.json.JsonUtil;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.LogicRequest;
import io.micrc.lib.ClassCastUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        routeTemplate(ROUTE_TMPL_BUSINESSES_SERVICE)
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("logicName", null, "the logicName")
                .templateParameter("targetIdPath", null, "the targetIdPath")
                .templateParameter("commandParamIntegrationsJson", null, "the command integration params")
                .templateParameter("aggregationName", null, "the aggregation name")
                .templateParameter("repositoryName", null, "the repositoryName name")
                .templateParameter("aggregationPath", null, "the aggregation full path")
                .templateParameter("logicName", null, "the logicName")
                .templateParameter("logicIntegrationJson", null, "the logic integration params")
                .from("businesses:{{serviceName}}")
                .transacted()
                .marshal().json().convertBodyTo(String.class)
                // 0.1 集成准备, 准备Source
                .setProperty("commandJson", body())
                // FIXME 这里有个BUG要修
                .setHeader("pointer", constant("{{targetIdPath}}"))
                .to("json-patch://select")
                .to("repository://{{repositoryName}}?method=findById")
                .setProperty("source", simple("${in.body.get}"))
                .bean(TargetSourceClone.class,
                        "clone(${exchange.properties.get(source)}, ${exchange.properties.get(commandJson)})")
                .setProperty("commandJson", body())
                // FIXME end
                // 1 分发集成
                .setProperty("commandParamIntegrationsJson", simple("{{commandParamIntegrationsJson}}"))
                .dynamicRouter(method(IntegrationCommandParams.class, "integrate"))
                // 2 执行逻辑
                .setProperty("logicName", simple("{{logicName}}"))
                .setProperty("logicIntegrationJson")
                .groovy("new String(java.util.Base64.getDecoder().decode('{{logicIntegrationJson}}'))")
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
                .to("repository://{{repositoryName}}?method=save")
                // TODO 仓库集成抽进repository路由内部 END
                // 3 消息存储
                .setBody(exchangeProperty("commandJson"))
                .to("eventstore://store")
                .setBody(exchangeProperty("commandJson"))
                .unmarshal().json(HashMap.class)
                .end();

        from("direct://integration-params")
                // 得到需要集成的集成参数
                .bean(IntegrationCommandParams.class,
                        "executableIntegrationInfo(${exchange.properties.get(unIntegrateParams)}, ${exchange.properties.get(commandJson)})")
                .setProperty("currentIntegrateParam", body())
                .setBody(simple("${in.body.get(integrateParams)}"))
                .setHeader("protocolFilePath", simple("${exchange.properties.get(currentIntegrateParam).get(protocol)}"))
                .to("req://integration")
                .process(exchange -> {
                    String body = (String) exchange.getIn().getBody();
                    JsonNode jsonNode = JsonUtil.readTree(body);
                    String resultCode = jsonNode.at("/code").textValue();
                    if ("200".equals(resultCode)) {
                        Map<String, Object> currentIntegrateParam = ClassCastUtils.castHashMap(
                                exchange.getProperty("currentIntegrateParam", Map.class), String.class, Object.class);
                        Map<String, CommandParamIntegration> unIntegrateParams = ClassCastUtils.castHashMap(
                                exchange.getProperty("unIntegrateParams", Map.class), String.class,
                                CommandParamIntegration.class);
                        JsonNode dataNode = jsonNode.at("/data");
                        if (null == dataNode) {
                            throw new RuntimeException("the param "
                                    + unIntegrateParams.get(currentIntegrateParam.get("paramName"))
                                    + " integrate return value is null, but the integrate result can`t be null, so you should check...");
                        }
                        String data = dataNode.toString();
                        String commandJson = patch((String) exchange.getProperties().get("commandJson"),
                                unIntegrateParams.get(currentIntegrateParam.get("paramName")).getObjectTreePath(),
                                data);
                        exchange.getProperties().put("commandJson", commandJson);
                        // 标识该参数已成功
                        unIntegrateParams.remove(currentIntegrateParam.get("paramName"));
                        exchange.getProperties().put("currentIntegrateParam", currentIntegrateParam);
                        exchange.getProperties().put("unIntegrateParams", unIntegrateParams);
                    }
                })
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
                .bean(LogicInParamsResolve.class, "toLogicParams(${body}, ${exchange.properties.get(commandJson)})")
                .setHeader("logic", simple("${exchange.properties.get(logicName)}/logic"))
                .bean(LogicRequest.class, "request")
                .unmarshal().json(HashMap.class)
                .bean(LogicInParamsResolve.class,
                        "toTargetParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(logicIntegrationJson)})")
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
         * 执行逻辑名(截取Command的一部分) - 内部获取
         */
        protected String logicName;

        /**
         * target仓库集成时Id位置 - 外部输入
         */
        protected String targetIdPath;

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
                String commandJson = patch((String) exchange.getProperties().get("commandJson"),
                        "/error",
                        JsonUtil.writeValueAsString(errorInfo));
                exchange.getProperties().put("commandJson", commandJson);
                exchange.getIn().setBody(commandJson);
//                exchange.setRollbackOnly(true);
//                // 抛出一个异常
//                throw new RuntimeException("check result is null, please check dmn...");
            }
            if (!(Boolean) checkResult.get("checkResult")) {
                errorInfo.setErrorCode((String) checkResult.get("errorCode"));
                String commandJson = patch((String) exchange.getProperties().get("commandJson"),
                        "/error",
                        JsonUtil.writeValueAsString(errorInfo));
                exchange.getProperties().put("commandJson", commandJson);
                exchange.getIn().setBody(commandJson);
//                exchange.setRollbackOnly(true);
//                // 抛出一个异常
//                 throw new RuntimeException((String) checkResult.get("errorCode"));
//                         , (String) checkResult.get("errorMessage"));
            }
        }
    }
}

/**
 * 逻辑执行参数处理
 */
class LogicInParamsResolve {

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public String toLogicParams(LogicIntegration logicIntegration, String commandJson) {
        Map<String, Object> logicParams = new HashMap<>();
        logicIntegration.getOutMappings().keySet().stream().forEach(key -> {
            String outMapping = JsonUtil.readTree(commandJson).at(logicIntegration.getOutMappings().get(key))
                    .toString();
            Object value = JsonUtil.writeValueAsObject(outMapping, Object.class);
            if (null == value) {
                throw new RuntimeException(logicIntegration.getOutMappings().get(key)
                        + " - the params can`t get value, please check the annotation.like integration annotation error or toLogicMappings annotation have error ");
            }
            logicParams.put(key, value);
        });
        return JsonUtil.writeValueAsStringRetainNull(logicParams);
    }

    public String toTargetParams(Map<String, Object> logicResult, String commandJson, String logicIntegrationJson) {
        LogicIntegration logicIntegration = JsonUtil.writeValueAsObject(logicIntegrationJson, LogicIntegration.class);
        for (String key : logicResult.keySet()) {
            Object value = logicResult.get(key);
            // 当值序列化后为对象的时候
            if (value instanceof HashMap) {
                Map<String, Object> valueMap = ClassCastUtils.castHashMap(value, String.class, Object.class);
                logicResult.put(key, valueMap);
                valueMap.keySet().forEach(innerKey -> {
                    valueMap.put(innerKey, formatTimeValue(valueMap.get(innerKey)));
                });
            }
            // TODO 当值序列化后为List的时候
            logicResult.put(key, formatTimeValue(logicResult.get(key)));
            commandJson = patch(commandJson, logicIntegration.getEnterMappings().get(key),
                    JsonUtil.writeValueAsStringRetainNull(logicResult.get(key)));
        }
        return commandJson;
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

    private Object formatTimeValue(Object value) {
        if (value instanceof String && value.toString().contains("-") && value.toString().contains("T")
                && value.toString().contains(".") && value.toString().contains("+") && value.toString().contains(":")) {
            try {
                value = this.parseDate2Timestamp((String) value);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
        return value;
    }

    private long parseDate2Timestamp(String date) throws ParseException {
        return this.toDate(date, DATE_FORMAT).getTime();
    }

    private Date toDate(String date, String format) {
        if ("".equals(format) || format == null) {
            format = DATE_FORMAT;
        }
        try {
            return new SimpleDateFormat(format).parse(date);
        } catch (ParseException e) {
            return new Date();
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
            String protocolContent = fileReader(unIntegrateParams.get(key).getProtocol());
            JsonNode protocolNode = JsonUtil.readTree(protocolContent);
            JsonNode mappingNode = protocolNode
                    .at("/paths").elements().next().elements().next()
                    .at("/requestBody/content").elements().next()
                    .at("/x-integrate-mapping");
            Map<String, Object> integrateMappings = ClassCastUtils.castHashMap(
                    JsonUtil.writeValueAsObject(mappingNode.toString(),
                            HashMap.class),
                    String.class, Object.class);
            // 要求当前集成的所有映射均能够获取到其参数
            Boolean canExecute = integrateMappings.keySet().stream().allMatch(mappingKey -> {
                return null != JsonUtil.readTree(commandJson).at((String) integrateMappings.get(mappingKey));
            });
            // 如果当前循环的这个集成不能执行,则跳过该集成检查下一个
            if (!canExecute) {
                continue;
            }
            // 如果能够集成,收集信息,然后会自动跳出循环
            executableIntegrationInfo.put("paramName", unIntegrateParams.get(key).getParamName());
            executableIntegrationInfo.put("protocol", unIntegrateParams.get(key).getProtocol());
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
            HashMap<String, String> integrateParams = new HashMap<>();
            integrateMappings.keySet().forEach(mappingKey -> {
                integrateParams.put(mappingKey,
                        JsonUtil.readTree(commandJson).at((String) integrateMappings.get(mappingKey)).toString());
            });
            executableIntegrationInfo.put("integrateParams", integrateParams);
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
