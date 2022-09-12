package io.micrc.core.rpc.springboot;

import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;

/**
 * rpc rest服务端点适配器扫描，注入路由参数bean，用于每个适配器生成路由
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-13 07:09
 */
public class ClassPathRestEndpointScannerRegistrar implements ImportBeanDefinitionRegistrar {
    
}
