package io.micrc.core.message.springboot;

import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;


/**
 * message auto configuration. 注册publish/subscribe组件
 *
 * @author weiguan
 * @date 2022-09-06 19:29
 * @since 0.0.1
 */
@AutoConfigureAfter(CamelAutoConfiguration.class)
@Configuration
@EnableKafka
@Import({
})
public class MessageAutoConfiguration {


}
