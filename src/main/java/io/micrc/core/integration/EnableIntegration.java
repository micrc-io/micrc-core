package io.micrc.core.integration;

import io.micrc.core.integration.command.businesses.EnableCommandAdapter;
import io.micrc.core.integration.command.message.EnableMessageAdapter;

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
public @interface EnableIntegration {
    
}
