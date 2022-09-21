package io.micrc.core.integration.presentations;

import io.micrc.core.integration.presentations.springboot.ClassPathPresentationsAdapterScannerRegistrar;
import io.micrc.core.integration.presentations.springboot.PresentationsAdapterAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 消息接收适配器启动注解，用于客户端程序启用消息接收支持
 *
 * @author hyosunghan
 * @date 2022-09-13 19:02
 * @since 0.0.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({ClassPathPresentationsAdapterScannerRegistrar.class, PresentationsAdapterAutoConfiguration.class})
public @interface EnablePresentationsAdapter {
    String[] servicePackages() default {};
}
