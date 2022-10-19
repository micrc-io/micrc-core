package io.micrc.core.integration;

import io.micrc.core.integration.businesses.EnableCommandAdapter;
import io.micrc.core.integration.camel.EnableCamelRouteAdapter;
import io.micrc.core.integration.derivations.EnableDerivationsAdapter;
import io.micrc.core.integration.message.EnableMessageAdapter;
import io.micrc.core.integration.presentations.EnablePresentationsAdapter;

import java.lang.annotation.*;

/**
 * 整体集成支持启动注解，用于客户端程序便捷启动所有集成端口支持
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableMessageAdapter
@EnableCommandAdapter
@EnablePresentationsAdapter
@EnableDerivationsAdapter
@EnableCamelRouteAdapter
public @interface EnableIntegration {

}
