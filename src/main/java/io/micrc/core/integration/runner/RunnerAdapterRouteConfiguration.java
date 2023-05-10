package io.micrc.core.integration.runner;

import io.micrc.core.AbstractRouteTemplateParamDefinition;
import io.micrc.core.MicrcRouteBuilder;
import io.micrc.core.rpc.ErrorInfo;
import io.micrc.core.rpc.Result;
import io.micrc.lib.FileUtils;
import io.micrc.lib.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.camel.ExchangeProperties;

import java.util.List;
import java.util.Map;

/**
 * 应用业务服务适配器路由定义和参数bean定义
 *
 * @author tengwang
 * @date 2022-09-05 14:00
 * @since 0.0.1
 */
public class RunnerAdapterRouteConfiguration extends MicrcRouteBuilder {

    public static final String ROUTE_TMPL_RUNNER_ADAPTER = RunnerAdapterRouteConfiguration.class
            .getName() + ".runnerAdapter";

    @Override
    public void configureRoute() throws Exception {

        // 其他错误
        onException(Exception.class)
                .handled(true)
                .to("error-handle://system");

        routeTemplate(ROUTE_TMPL_RUNNER_ADAPTER)
                .templateParameter("name", null, "the adapter name")
                .templateParameter("serviceName", null, "the business service name")
                .templateParameter("executeContent", null, "the command full path")
                .from("runner:{{name}}?exchangePattern=InOut")
                .setProperty("serviceName", simple("{{serviceName}}"))
                .setProperty("executeContent", simple("{{executeContent}}"))
                .setProperty("commandPath", simple("{{commandPath}}"))
                // 1.执行文件转换
                .to("direct://executor-content-mapping")
                // 2.转换命令
                .to("direct://convert-command")
                // 3.执行逻辑
                .toD("bean://${exchange.properties.get(serviceName)}?method=execute")
                .end();

        from("direct://executor-content-mapping")
                .setBody(simple("${exchange.properties.get(executeContent)}"));
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @SuperBuilder
    public static class RunnerRouteTemplateParamDefinition extends AbstractRouteTemplateParamDefinition {

        private String name;

        private String serviceName;

        private String executeContent;

        private String commandPath;

    }
}
