package io.micrc.core.application.businesses;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.framework.json.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.BeanUtils;

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
public class ApplicationBusinessesServiceRouteConfiguration extends RouteBuilder {
    // 路由模版ID
    public static final String ROUTE_TMPL_BUSINESSES_SERVICE =
            ApplicationBusinessesServiceRouteConfiguration.class.getName() + ".businessesService";

    @Override
    public void configure() throws Exception {
        routeTemplate(ROUTE_TMPL_BUSINESSES_SERVICE)
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("commandPath", null, "the command full path")
                .templateParameter("logicName", null, "the logicName")
                .templateParameter("targetIdPath", null, "the targetIdPath")
                .templateParameter("commandParamIntegrationsJson", null, "the command integration params")
                .templateParameter("logicIntegrationJson", null, "the logic integration params")
                .templateParameter("aggregationName", null, "the aggregation name")
                .templateParameter("repositoryName", null, "the repositoryName name")
                .templateParameter("aggregationPath", null, "the aggregation full path")
                .from("businesses:{{serviceName}}?exchangePattern=InOut")
                .setProperty("command", body())
                .setProperty("target", simple("${in.body.target}"))
                .setProperty("commandPath", constant("{{commandPath}}"))
                .setProperty("commandParamIntegrationsJson", constant("{{commandParamIntegrationsJson}}"))
                .setProperty("logicIntegrationJson").groovy("new String(java.util.Base64.getDecoder().decode('{{logicIntegrationJson}}'))")
                .setProperty("aggregationPath", constant("{{aggregationPath}}"))
                // 0 Json化Command-整个使用过程中,均要操作该属性,最后返回拷贝至原始对象中
                .marshal().json(JsonLibrary.Jackson).convertBodyTo(String.class)
                .setProperty("commandJson", body())
                .setBody(exchangeProperty("commandParamIntegrationsJson"))
                .unmarshal().json(JsonLibrary.Jackson, List.class)
                .setProperty("commandParamIntegrations", body())
                .setBody(exchangeProperty("commandJson"))
                // 0.1 集成准备, 准备Source
                .setBody(exchangeProperty("commandJson"))
                .split().jsonpath("{{targetIdPath}}")
                .to("repository://{{repositoryName}}?method=findById")
                //.bean("{{repositoryName}}", "findById(${in.body})")
                .setProperty("source", simple("${in.body.get}"))
                .setProperty("target", simple("${in.body.get}"))
                .bean(TargetSourceClone.class, "clone(${exchange.properties.get(source)}, ${exchange.properties.get(commandJson)})")
                .setProperty("commandJson", body())
                // 1 分发集成
                // 使用DynamicRoute
                .dynamicRouter(method(IntegrationCommandParams.class, "integrate"))
                // 2 执行逻辑
                // 2.1 执行前置校验
                .setBody(exchangeProperty("commandJson"))
                .to("logic://post:/{{logicName}}/before?host=localhost:8080")
                .unmarshal().json(JsonLibrary.Jackson, CheckResult.class)
                .bean(logicCheckedResultCheck.class, "check(${body})")
//                // 2.2 执行逻辑
                .setBody(exchangeProperty("logicIntegrationJson"))
                .unmarshal().json(JsonLibrary.Jackson, LogicIntegration.class)
                .bean(LogicInParamsResolve.class, "toLogicParams(${body}, ${exchange.properties.get(commandJson)})")
                .to("logic://post:/{{logicName}}/logic?host=localhost:8080")
                .unmarshal().json(JsonLibrary.Jackson, HashMap.class)
                .bean(LogicInParamsResolve.class, "toTargetParams(${body}, ${exchange.properties.get(commandJson)}, ${exchange.properties.get(logicIntegrationJson)})")
                .setProperty("commandJson", body())
//                // 2.3 执行后置校验
                .to("logic://post:/{{logicName}}/before?host=localhost:8080")
                .unmarshal().json(JsonLibrary.Jackson, CheckResult.class)
                .bean(logicCheckedResultCheck.class, "check(${body})")
                .setBody(exchangeProperty("commandJson"))
                .bean(RepositoryParamsResolve.class, "process(${exchange.properties.get(commandJson)}, ${exchange.properties.get(aggregationPath)})")
                .setProperty("target", simple("${in.body}"))
                .setBody(simple("${in.body}"))
                .to("repository://{{repositoryName}}?method=save")
                // 3 TODO  消息存储
//                .bean(Message.class, "sendMessage(${exchange.properties.command}, ${exchange.properties.command.event)}")
//                .bean("messageRepository", "save")
                // 4.拷贝至原有target上
                .bean(BeanCopy.class, "copy(${exchange.properties.get(commandJson)}, ${exchange.properties.get(command)})")
                .setBody(exchangeProperty("command"))
                .bean(BodyHandler.class, "getBody")
        ;
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
         * 命令对象全路径名称 - 内部获取
         */
        protected String commandPath;

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

    public static class TargetSourceClone {
        // TODO 考虑仓库集成于衍生集成混杂的情况

        public static final String sourcePatchString = "[{ \"op\": \"replace\", \"path\": \"/source\", \"value\": {{value}} }]";

        public static final String targetPatchString = "[{ \"op\": \"replace\", \"path\": \"/target\", \"value\": {{value}} }]";

        public static String clone(Object target, String commandJson) {
            try {
                String sourceReplacedJson = sourcePatchString.replace("{{value}}", JsonUtil.writeValueAsStringRetainNull(target));
                String targetReplacedJson = targetPatchString.replace("{{value}}", JsonUtil.writeValueAsStringRetainNull(target));
                // 先用jsonPatch更新指令
                JsonPatch sourcePatch = JsonPatch.fromJson(JsonUtil.readTree(sourceReplacedJson));
                JsonNode sourceReplacedApply = sourcePatch.apply(JsonUtil.readTree(commandJson));
                JsonPatch targetPatch = JsonPatch.fromJson(JsonUtil.readTree(targetReplacedJson));
                JsonNode targetReplacedApply = targetPatch.apply(sourceReplacedApply);
                return JsonUtil.writeValueAsStringRetainNull(targetReplacedApply);
            } catch (IOException | JsonPatchException e) {
                throw new ServiceExecuteException("patch to source failed, please check Target is inited?...");
            }
        }
    }

    public static class logicCheckedResultCheck {
        public static void check(CheckResult checkResult) {
            if (null == checkResult.getCheckResult()) {
                // 抛出一个异常
                throw new LogicExecuteException("check result is null, please check dmn...");
            }
            if (!checkResult.getCheckResult()) {
                // 抛出一个异常
                throw new LogicExecuteException(checkResult.getErrorCode(), checkResult.getErrorMessage());
            }
        }
    }
}


class IntegrationCommandParams {

    public static final String patchString = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
    private static final String mappingJsonPath = "$.paths..requestBody.content..x-integrate-mapping";
    private static final String serviceJsonPath = "$.servers";
    private static final String operationIdJsonPath = "$.paths..operationId";

    public static String integrate(String body, @ExchangeProperties Map<String, Object> properties) {
        List<CommandParamIntegration> commandParamIntegrations;
        commandParamIntegrations = (List<CommandParamIntegration>) properties.get("commandParamIntegrations");
        // camel的unmarshal不彻底,自己再转换一下
        commandParamIntegrations = JsonUtil.writeObjectAsList(commandParamIntegrations, CommandParamIntegration.class);
        // 本身无需集成
        if (commandParamIntegrations.size() == 0) {
            return null;
        }
        String commandJson = (String) properties.get("commandJson");
        String currentIntegrateParam = (String) properties.get("currentIntegrateParam");
        // 需要进行集成
        if (null != currentIntegrateParam && commandParamIntegrations.size() > 0) {
            // 将上次执行的结果放回至原有属性集成参数之中(当本次集成是失败的时候,不能重试,需要控制指针位移至下一个,跑圈)
            Integer resultCode = JsonPath.read(body, "$.code");
            if (null != resultCode && resultCode >= 200 && resultCode < 300) {
                // 标识该参数已成功
                List<CommandParamIntegration> controlParam = commandParamIntegrations.stream().filter(commandParamIntegration -> currentIntegrateParam.equals(commandParamIntegration.getParamName())).collect(Collectors.toList());
                controlParam.get(0).setIntegrationComplete(true);
                Object retval = JsonPath.read(body, "$.data");
                if (null == retval) {
                    throw new ServiceExecuteException("the param " + controlParam.get(0).getParamName() + " integrate return value is null, but the integrate result can`t be null, so you should check the protocol " + controlParam.get(0).getProtocol());
                }
                String data = JsonUtil.writeValueAsStringRetainNull(JsonPath.read(body, "$.data"));
                commandJson = patch(commandJson, controlParam.get(0).getObjectTreePath(), data);
                properties.put("commandJson", commandJson);
                properties.put("commandParamIntegrations", commandParamIntegrations);
            }
        }
        List<CommandParamIntegration> unIntegrateParams = commandParamIntegrations.stream().filter(commandParamIntegration -> commandParamIntegration.getIntegrationComplete() != true).collect(Collectors.toList());
        // 成功完成
        if (unIntegrateParams.size() == 0) {
            body = commandJson;
            return null;
        }
        // 未完成需要继续执行
        Map<String, Object> executableIntegrationInfo = executableIntegrationInfo(unIntegrateParams, commandJson);
        // 切换上下文
        body = JsonUtil.writeValueAsStringRetainNull(executableIntegrationInfo.get("integrateParams"));
        properties.put("currentIntegrateParam", executableIntegrationInfo.get("paramName"));
        String routeEndPoint = "bean://io.micrc.core.application.businesses.BodyHandler?method=getBody(" + body + "),  rest-openapi://" + executableIntegrationInfo.get("protocol") + "#" + executableIntegrationInfo.get("operationId") + "?host=" + executableIntegrationInfo.get("host");
        return routeEndPoint;
    }

    private static String patch(String original, String path, String value) {
        try {
            String pathReplaced = patchString.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            // 先用jsonPatch更新指令
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new ServiceExecuteException("integration result can not put command, please check objectTreePath...");
        }
    }

    /**
     * 得到一个可执行的集成的集成信息
     *
     * @param unIntegrateParams
     * @return
     */
    private static Map<String, Object> executableIntegrationInfo(List<CommandParamIntegration> unIntegrateParams, String commandJson) {
        Map<String, Object> executableIntegrationInfo = new HashMap<>();
        Integer checkNumber = -1;
        do {
            checkNumber++;
            // 没有可执行的集成了,抛异常
            if (checkNumber >= unIntegrateParams.size()) {
                throw new ServiceExecuteException("the integration file have error, command need integrate, but the param can not use... ");
            }
            String protocolContent = fileReader(unIntegrateParams.get(checkNumber).getProtocol());
            Configuration conf = Configuration.builder().options(Option.AS_PATH_LIST).build();
            List<String> integrationMappingsPaths = JsonPath.parse(protocolContent, conf).read(mappingJsonPath);
            if (integrationMappingsPaths.size() != 1) {
                throw new ServiceDesignException(unIntegrateParams.get(checkNumber).getProtocol() + " - the integration openapi mappings have error, please check... ");
            }
            Map<String, Object> integrateMappings = JsonPath.parse(protocolContent).read(integrationMappingsPaths.get(0));
            // 要求当前集成的所有映射均能够获取到其参数
            Boolean canExecute = integrateMappings.keySet().stream().allMatch(key -> {
                Object integrateParamValue = JsonPath.read(commandJson, (String) integrateMappings.get(key));
                return null != integrateParamValue;
            });
            // 如果当前循环的这个集成不能执行,则跳过该集成检查下一个
            if (!canExecute) {
                continue;
            }
            // 如果能够集成,收集信息,然后会自动跳出循环
            executableIntegrationInfo.put("paramName", unIntegrateParams.get(checkNumber).getParamName());
            executableIntegrationInfo.put("protocol", unIntegrateParams.get(checkNumber).getProtocol());
            // 收集host
            List<Map<String, Object>> servicesPaths = JsonPath.parse(protocolContent).read(serviceJsonPath);
            if (servicesPaths.size() != 1) {
                throw new ServiceDesignException(unIntegrateParams.get(checkNumber).getProtocol() + " - the openapi servers have error, we need only one services, but not found or found > 1.....");
            }
            Map<String, Object> serviceMaps = servicesPaths.get(0);
            executableIntegrationInfo.put("host", serviceMaps.get("url"));
            // 收集operationId
            List<String> operationIdJsonPaths = JsonPath.parse(protocolContent, conf).read(operationIdJsonPath);
            if (operationIdJsonPaths.size() != 1) {
                throw new ServiceDesignException(unIntegrateParams.get(checkNumber).getProtocol() + " - we can not support muti-func openapi, please check... ");
            }
            executableIntegrationInfo.put("operationId", JsonPath.parse(protocolContent).read(operationIdJsonPaths.get(0)));
            HashMap<String, String> integrateParams = new HashMap<>();
            integrateMappings.keySet().stream().forEach(key -> {
                String integrateParamValue = JsonUtil.writeValueAsStringRetainNull(JsonPath.read(commandJson, (String) integrateMappings.get(key)));
                integrateParams.put(key, integrateParamValue);
            });
            executableIntegrationInfo.put("integrateParams", integrateParams);
        } while (null == executableIntegrationInfo.get("paramName"));
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
            int length = 0;
            while ((str = in.readLine()) != null) {
                fileContent.append(str);
            }
            in.close();
        } catch (IOException e) {
            throw new ServiceExecuteException(filePath + " file not found or can`t resolve...");
        }
        return fileContent.toString();
    }
}


/**
 * 逻辑执行参数处理
 */
@Slf4j
class LogicInParamsResolve {

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String patchString = "[{ \"op\": \"replace\", \"path\": \"{{path}}\", \"value\": {{value}} }]";

    private static String patch(String original, String path, String value) {
        try {
            String pathReplaced = patchString.replace("{{path}}", path);
            String valueReplaced = pathReplaced.replace("{{value}}", value);
            // 先用jsonPatch更新指令
            JsonPatch patch = JsonPatch.fromJson(JsonUtil.readTree(valueReplaced));
            return JsonUtil.writeValueAsStringRetainNull(patch.apply(JsonUtil.readTree(original)));
        } catch (IOException | JsonPatchException e) {
            throw new ServiceExecuteException("integration result can not put command, please check objectTreePath...");
        }
    }

    public String toLogicParams(LogicIntegration logicIntegration, String commandJson) {
        Map<String, Object> logicParams = new HashMap<>();
        logicIntegration.getOutMappings().keySet().stream().forEach(key -> {
            Object value = JsonPath.read(commandJson, logicIntegration.getOutMappings().get(key));
            if (null == value) {
                throw new ServiceDesignException(logicIntegration.getOutMappings().get(key) + " - the params can`t get value, please check the annotation.like integration annotation error or toLogicMappings annotation have error ");
            }
            logicParams.put(key, value);
        });
        return JsonUtil.writeValueAsStringRetainNull(logicParams);
    }

    public String toTargetParams(HashMap<String, Object> logicResult, String commandJson, String logicIntegrationJson) {
        LogicIntegration logicIntegration = JsonUtil.writeValueAsObject(logicIntegrationJson, LogicIntegration.class);
        for (String key : logicResult.keySet()) {
            Object value = logicResult.get(key);
            // 当值序列化后为对象的时候
            if (value instanceof HashMap) {
                HashMap<String, Object> valueMap = (HashMap<String, Object>) value;
                valueMap.keySet().stream().forEach(innerKey -> {
                    valueMap.put(innerKey, formatTimeValue(valueMap.get(innerKey)));
                });
            }
            // TODO 当值序列化后为List的时候
            logicResult.put(key, formatTimeValue(logicResult.get(key)));
            commandJson = patch(commandJson, logicIntegration.getEnterMappings().get(key), JsonUtil.writeValueAsStringRetainNull(logicResult.get(key)));
        }
        return commandJson;
    }

    private Object formatTimeValue(Object value) {
        if (null != value && value.toString().contains("-") && value.toString().contains("T") && value.toString().contains(".") && value.toString().contains("+") && value.toString().contains(":")) {
            try {
                value = this.parseDate2Timestamp((String) value);
            } catch (ParseException e) {
                throw new LogicExecuteException(e);
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

class RepositoryParamsResolve {
    public Object process(String commandJson, String classPath) throws ClassNotFoundException {
        HashMap targetMap = JsonPath.parse(commandJson).read("$.target");
        return JsonUtil.writeObjectAsObject(targetMap, Class.forName(classPath));
    }
}

class BodyHandler {
    public static String getBody(String body) {
        return body;
    }
}

class BeanCopy {

    void copy(Object source, Object target) {
        BeanUtils.copyProperties(JsonUtil.writeValueAsObject((String) source, target.getClass()), target);
    }
}