package io.micrc.core.message.springboot;

import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;

/**
 * 消息监听适配器扫描，注入路由参数bean，用于每个适配器生成路由
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-13 05:30
 */
public class ClassPathMessageSubscriberScannerRegistrar implements ImportBeanDefinitionRegistrar {

}
