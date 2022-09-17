package io.micrc.core.integration.command.businesses;

import com.jayway.jsonpath.JsonPath;
import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.framework.json.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
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

    public static final String ROUTE_TMPL_BUSINESSES_ADAPTER =
            ApplicationCommandAdapterRouteConfiguration.class.getName() + ".businessesAdapter";

    @Override
    public void configureRoute() throws Exception {

        routeTemplate(ROUTE_TMPL_BUSINESSES_ADAPTER)
                .templateParameter("name", null, "the adapter name")
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("commandPath", null, "the command full path")
                .templateParameter("conceptionsJson", null, "the conceptions json")
                .from("command-adapter:{{name}}?exchangePattern=InOut")
                .setProperty("commandPath", constant("{{commandPath}}"))
                .setProperty("conceptionsJson", constant("{{conceptionsJson}}"))
                .setProperty("paramsJson", body())
                .dynamicRouter(method(AdapterParamsHandler.class, "convert"))
                .setBody(exchangeProperty("command"))
                .marshal().json().convertBodyTo(String.class)
                .to("businesses://{{serviceName}}")
//                .setProperty("commandJson", body())
//                .setHeader("pointer", simple("/error"))
//                .to("json-patch://select")
//                .setProperty("error", body())
//                // TODO 设置command对Data的映射,使用protocol读取其x-result-mapping.暂时使用command替代
//                .bean(Result.class, "result(${error}, ${commandJson})")
        ;
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

        private String conceptionsJson;
    }
}


class AdapterParamsHandler {
    public static String convert(
            String body,
            @ExchangeProperties Map<String, Object> properties
    ) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String paramsJson = (String) properties.get("paramsJson");
        List<ConceptionParam> conceptions = (List<ConceptionParam>) properties.get("conceptions");
        Object command = properties.get("command");
        // 处理第一次进来时的情况
        if (null == conceptions && null == command) {
            String conceptionsJson = (String) properties.get("conceptionsJson");
            conceptions = JsonUtil.writeValueAsList(conceptionsJson, ConceptionParam.class);
            properties.put("conceptions", conceptions);
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
                    Optional<Method> targetMethodOptional = Arrays.stream(methods).filter(method -> method.getName().equals("set" + upperStringFirst(conception.getCommandInnerName()))).findFirst();
                    if (targetMethodOptional.isEmpty()) {
                        throw new ApplicationCommandAdapterDesignException("command not found method to set value " + conception.getName() + ", the target conception name is " + conception.getCommandInnerName());
                    }
                    Method targetMethod = targetMethodOptional.get();
                    Class<?>[] parameterTypes = targetMethod.getParameterTypes();
                    if (parameterTypes.length != 1) {
                        throw new ApplicationCommandAdapterDesignException("command target conception can not to set, please check target method params ");
                    }
                    Object value = JsonUtil.writeValueAsObject(body, parameterTypes[0]);
                    targetMethod.invoke(command, value);
                    conception.setResolved(true);
                }
            }
        }
        assert conceptions != null;
        List<ConceptionParam> unResolveParams = conceptions.stream().filter(conceptionParam -> !conceptionParam.getResolved()).collect(Collectors.toList());
        // 所有都处理完了
        if (0 == unResolveParams.size()) {
            return null;
        }
        unResolveParams.sort(Comparator.comparing(ConceptionParam::getOrder));
        ConceptionParam conceptionParam = unResolveParams.get(0);
        properties.put("currentResolveParam", conceptionParam.getName());
        Object targetConception = JsonPath.parse(paramsJson).read("$." + conceptionParam.getName());
        body = JsonUtil.writeValueAsString(targetConception);
        return "bean://io.micrc.core.integration.command.businesses.BodyHandler?method=getBody(" + body + "), jslt://" + conceptionParam.getTargetConceptionMappingPath();
    }


    private static String upperStringFirst(String str) {
        char[] strChars = str.toCharArray();
        strChars[0] -= 32;
        return String.valueOf(strChars);
    }
}

class BodyHandler {
    public static String getBody(String body) {
        return body;
    }
}
