package io.micrc.core.application.presentations;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
        routeTemplate(ROUTE_TMPL_PRESENTATIONS_SERVICE)
                // 设置模板参数
                .templateParameter("serviceName", null, "the presentations service name")
                .templateParameter("paramIntegrationsJson", null, "the command integration params")
                .templateParameter("assembler", null, "assembler")
                // 指定service名称为入口端点
                .from("presentations:{{serviceName}}")
                .setProperty("paramIntegrationsJson", simple("{{paramIntegrationsJson}}"))
                .setProperty("param", simple("${in.body}")) // 存储入参到交换区，动态处理及结果汇编需要
                // 动态路由解析
                .dynamicRouter(method(IntegrationParams.class, "dynamicIntegrate"))
                // 汇编结果处理
                .setBody(simple("${exchange.properties.get(param)}"))
                // .to("jslt://{{assembler}}")
                .setHeader("mappingFilePath", simple("{{assembler}}"))
                .to("json-mapping://file")
                // 出口
                .end();

        from("direct://presentations-integration")
                // 得到需要集成的集成参数，todo,封装进动态路由
                .bean(IntegrationParams.class, "findExecutable(${exchange.properties.get(unIntegrateParams)}, ${exchange.properties.get(param)})")
                .setProperty("current", body())
                // 构造发送
                .choice()
                .when(constant("QUERY").isEqualTo(simple("${exchange.properties.get(current).get(type)}")))
                .bean(IntegrationParams.class, "executeQuery")
                .endChoice()
                .otherwise()
                .setBody(simple("${exchange.properties.get(current).get(params)}"))
                .to("req://integration")
                .endChoice()
                .end()
                  // 处理返回
                .bean(IntegrationParams.class, "processResult");
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
        if (unIntegrateParams.size() == 0) {
            return null;
        }
        return "direct://presentations-integration";
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
        String param = (String) properties.get("param");
        Map<String, Object> current = ClassCastUtils.castHashMap(properties.get("current"), String.class, Object.class);
        String name = (String) current.get("name");
        ParamIntegration.Type currentIntegrateType = (ParamIntegration.Type) current.get("type");
        if (ParamIntegration.Type.QUERY.equals(currentIntegrateType) && body instanceof Optional) {
            body = ((Optional<?>) body).orElseThrow();
        } else if (ParamIntegration.Type.INTEGRATE.equals(currentIntegrateType)) {
            body = JsonUtil.readPath((String) body, "/data");
        }
        List<ParamIntegration> paramIntegrations = ClassCastUtils.castArrayList(exchange.getProperties().get("paramIntegrations"), ParamIntegration.class);
        // 将上次执行的结果放回至原有属性集成参数之中
        ParamIntegration find = paramIntegrations.stream()
                .filter(paramIntegration -> name.equals(paramIntegration.getConcept()))
                .findFirst().orElseThrow();
        // 标识该参数已成功
        find.setIntegrationComplete(true);
        param = add(param, "/" + find.getConcept(), JsonUtil.writeValueAsString(body));
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
    public static Map<String, Object> findExecutable(List<ParamIntegration> unIntegrateParams, String param) {

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
                paramMap.put(name, JsonUtil.readPath(param, path));
            });
            // 检查当前查询是否可执行
            if (paramMap.values().stream().anyMatch(Objects::isNull)) {
                continue;
            }
            System.out.println(paramMap);
            if (ParamIntegration.Type.QUERY.equals(paramIntegration.getType())) {
                // 如果能够集成,收集信息,然后会自动跳出循环
                executableIntegrationInfo.put("aggregation", paramIntegration.getAggregation());
                executableIntegrationInfo.put("method", paramIntegration.getQueryMethod());
                executableIntegrationInfo.put("sorts", paramIntegration.getSortParams());
                executableIntegrationInfo.put("pageSizePath", paramIntegration.getPageSizePath());
                executableIntegrationInfo.put("pageNumberPath", paramIntegration.getPageNumberPath());
                executableIntegrationInfo.put("params", paramMap);
            } else if (ParamIntegration.Type.INTEGRATE.equals(paramIntegration.getType())) {
                // 集成
                String protocolContent = fileReader(unIntegrateParams.get(checkNumber).getProtocol());
                JsonNode protocolNode = JsonUtil.readTree(protocolContent);
                // 如果能够集成,收集信息,然后会自动跳出循环
                executableIntegrationInfo.put("protocol", paramIntegration.getProtocol());
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

                executableIntegrationInfo.put("params", JsonUtil.writeValueAsString(paramMap));
            }
            executableIntegrationInfo.put("name", paramIntegration.getConcept());
            executableIntegrationInfo.put("type", paramIntegration.getType());
        } while (null == executableIntegrationInfo.get("name"));
        return executableIntegrationInfo;
    }

    /**
     * 执行查询
     *
     * @return
     */
    public static Object executeQuery(Exchange exchange, Map<String, Object> body) {
        Object pageSize = JsonUtil.readPath((String) exchange.getProperty("param"), (String) body.get("pageSizePath"));
        Object pageNumber = JsonUtil.readPath((String) exchange.getProperty("param"), (String) body.get("pageNumberPath"));
        PageRequest pageRequest = null;
        if (pageSize != null && pageNumber != null) {
            pageRequest = PageRequest.of(((int) pageNumber) - 1, (int) pageSize);
            // 追加排序参数
            Sort sort = Sort.unsorted();
            Map<String, String> sorts = ClassCastUtils.castHashMap(body.get("sorts"), String.class, String.class);
            for (Map.Entry<String, String> next : sorts.entrySet()) {
                sort = sort.and(Sort.by(Sort.Direction.valueOf(next.getValue()), next.getKey()));
            }
            pageRequest = pageRequest.withSort(sort);
        }
        try {
            Object repository = exchange.getContext().getRegistry().lookupByName(body.get("aggregation") + "Repository");
            Method method = Arrays.stream(repository.getClass().getMethods())
                    .filter(m -> m.getName().equals(body.get("method"))).findFirst().orElseThrow();
            Class<?>[] parameterTypes = method.getParameterTypes();
            ArrayList<Object> parameters = new ArrayList<>();
            if (parameterTypes.length > 0 && "org.springframework.data.domain.Pageable".equals(parameterTypes[0].getName())) {
                parameters.add(pageRequest);
            }
            parameters.addAll(ClassCastUtils.castHashMap(body.get("params"), String.class, Object.class).values());
            return method.invoke(repository, parameters.toArray());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取文件
     *
     * @param filePath
     * @return
     */
    private static String fileReader(String filePath) {
        StringBuilder fileContent = new StringBuilder();
        try {
            InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
            assert stream != null;
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String str = null;
            while ((str = in.readLine()) != null) {
                fileContent.append(str);
            }
            in.close();
        } catch (IOException e) {
            throw new IllegalStateException(filePath + " file not found or can`t resolve...");
        }
        return fileContent.toString();
    }


    private static String add(String original, String path, String value) {
        String patchCommand = "[{ \"op\": \"add\", \"path\": \"{{path}}\", \"value\": {{value}} }]";
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