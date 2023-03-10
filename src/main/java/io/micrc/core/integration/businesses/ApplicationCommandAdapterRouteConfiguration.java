package io.micrc.core.integration.businesses;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.Result;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用业务服务适配器路由定义和参数bean定义
 *
 * @author tengwang
 * @date 2022-09-05 14:00
 * @since 0.0.1
 */
public class ApplicationCommandAdapterRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_BUSINESSES_ADAPTER = ApplicationCommandAdapterRouteConfiguration.class
            .getName() + ".businessesAdapter";

    @Override
    public void configureRoute() throws Exception {

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_BUSINESSES_ADAPTER)
                .templateParameter("name", null, "the adapter name")
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("commandPath", null, "the command full path")
                .templateParameter("requestMapping", null, "the request mapping context")
                .templateParameter("responseMapping", null, "the response mapping context")
                .from("command:{{name}}?exchangePattern=InOut")
                .setProperty("commandPath", constant("{{commandPath}}"))
                .setProperty("requestMapping", simple("{{requestMapping}}"))
                .setProperty("responseMapping", simple("{{responseMapping}}"))
                // 1.请求映射
                .to("direct://request-mapping-businesses")
                // 2.转换命令
                .to("direct://convert-command")
                // 3.执行逻辑
                .toD("bean://{{serviceName}}?method=execute")
                // 4.统一返回
                .to("direct://commandAdapterResult")
                .end();

        from("direct://convert-command")
                .setProperty("paramsJson", body())
                .bean(AdapterParamsHandler.class, "convertCommand")
                .setBody(exchangeProperty("command"));

        from("direct://request-mapping-businesses")
                .setHeader("mappingContent", exchangeProperty("requestMapping"))
                .to("json-mapping://content");

        from("direct://response-mapping-businesses")
                .setHeader("mappingContent", exchangeProperty("responseMapping"))
                .to("json-mapping://content");

        // 命令适配器结果处理
        from("direct://commandAdapterResult")
                .marshal().json().convertBodyTo(String.class)
                .setProperty("commandJson", body())
                // 结果转换
                .to("direct://response-mapping-businesses")
                .unmarshal().json(Object.class)
                .setProperty("commandResult", body())
                // 错误信息
                .setBody(exchangeProperty("commandJson"))
                .setHeader("pointer", simple("/error"))
                .to("json-patch://select")
                .marshal().json().convertBodyTo(String.class)
                .unmarshal().json(ErrorInfo.class)
                .setProperty("errorInfo", body())
                .bean(Result.class, "result(${body}, ${exchange.properties.get(commandResult)})");
    }

    /**
     * 应用业务服务适配器路由参数Bean
     *
     * @author tengwang
     * @date 2022-09-05 14:00
     * @since 0.0.1
     */
    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class ApplicationCommandRouteTemplateParamDefinition extends AbstractRouteTemplateParamDefinition {

        private String name;

        private String serviceName;

        private String commandPath;

        /**
         * 请求映射
         */
        String requestMapping;

        /**
         * 响应映射
         */
        String responseMapping;

//        private String conceptionsJson;
    }
}

class AdapterParamsHandler {

    public static void convertCommand(
            @ExchangeProperties Map<String, Object> properties) throws ClassNotFoundException {
        String paramsJson = (String) properties.get("paramsJson");
        Class<?> commandClazz = Class.forName((String) properties.get("commandPath"));
        Object commandInstance = JsonUtil.writeValueAsObject(paramsJson, commandClazz);
        properties.put("command", commandInstance);
    }

    public static String convert(
            Exchange exchange,
            @ExchangeProperties Map<String, Object> properties) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {
        String paramsJson = (String) properties.get("paramsJson");
        List<ConceptionParam> conceptions = ClassCastUtils.castArrayList(properties.get("conceptions"),
                ConceptionParam.class);
        Object command = properties.get("command");
        // 处理第一次进来时的情况
        if (null == conceptions) {
            String conceptionsJson = (String) properties.get("conceptionsJson");
            conceptions = JsonUtil.writeValueAsList(conceptionsJson, ConceptionParam.class);
            properties.put("conceptions", conceptions);
        }
        if (null == command) {
            Class<?> commandClazz = Class.forName((String) properties.get("commandPath"));
            Object commandInstance = commandClazz.getConstructor().newInstance();
            properties.put("command", commandInstance);
            command = commandInstance;
        }
        // 处理上一次返回结果
        String currentResolveParam = (String) properties.get("currentResolveParam");
        if (null != currentResolveParam) {
            assert conceptions != null;
            for (ConceptionParam conception : conceptions) {
                if (currentResolveParam.equals(conception.getName())) {
                    Method[] methods = command.getClass().getMethods();
                    Optional<Method> targetMethodOptional = Arrays.stream(methods).filter(method -> method.getName()
                            .equals("set" + upperStringFirst(conception.getCommandInnerName()))).findFirst();
                    if (targetMethodOptional.isEmpty()) {
                        throw new ApplicationCommandAdapterDesignException(
                                "command not found method to set value " + conception.getName()
                                        + ", the target conception name is " + conception.getCommandInnerName());
                    }
                    Method targetMethod = targetMethodOptional.get();
                    Class<?>[] parameterTypes = targetMethod.getParameterTypes();
                    if (parameterTypes.length != 1) {
                        throw new ApplicationCommandAdapterDesignException(
                                "command target conception can not to set, please check target method params ");
                    }
                    Object value = JsonUtil.writeValueAsObject(String.valueOf(exchange.getIn().getBody()), parameterTypes[0]);
                    targetMethod.invoke(command, value);
                    conception.setResolved(true);
                }
            }
        }
        assert conceptions != null;
        List<ConceptionParam> unResolveParams = conceptions.stream()
                .filter(conceptionParam -> !conceptionParam.getResolved()).collect(Collectors.toList());
        // 所有都处理完了
        if (0 == unResolveParams.size()) {
            return null;
        }
        unResolveParams.sort(Comparator.comparing(ConceptionParam::getOrder));
        ConceptionParam conceptionParam = unResolveParams.get(0);
        properties.put("currentResolveParam", conceptionParam.getName());
        String body = JsonUtil.readTree(paramsJson).at("/" + conceptionParam.getName()).toString();
        exchange.getIn().setHeader("mappingFilePath", conceptionParam.getTargetConceptionMappingPath());
        exchange.getIn().setBody(body);
        return "json-mapping://file";
    }

    private static String upperStringFirst(String str) {
        char[] strChars = str.toCharArray();
        strChars[0] -= 32;
        return String.valueOf(strChars);
    }
}
