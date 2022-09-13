package io.micrc.core._camel;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用路由和direct组件，临时实现各种没有的camel组件
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-13 16:38
 */
@Configuration
public class CamelComponentTempConfiguration {

    @Bean("jsonpatch")
    public DirectComponent jsonPatch() {
        return new DirectComponent();
    }

    @Bean
    public RoutesBuilder jsonPatchComp() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // TODO 使用java json-patch库实现两个路由(jsonpath:pointer, jsonpatch:patch)
                //      分别实现pointer取值和patch改值
            }
        };
    }
    
}
