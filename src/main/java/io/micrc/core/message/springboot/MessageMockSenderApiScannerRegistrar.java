package io.micrc.core.message.springboot;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.message.MessageAdapter;
import io.micrc.core.message.MessageMockSenderRouteConfiguration;
import io.micrc.core.message.MessageMockSenderRouteTemplateParameterSource;
import io.micrc.core.rpc.springboot.RpcEnvironmentProcessor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * message监听在default，local环境下的mock api，构建rest api发送消息，以触发监听
 * 扫描所有消息监听注解，生成rest api以模拟发送监听的消息
 *
 * @author weiguan
 * @date 2022-09-08 21:45
 * @since 0.0.1
 */
@Slf4j
public class MessageMockSenderApiScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment env;

    private ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        Optional<String> profileStr = Optional.ofNullable(env.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        if (!profiles.contains("local") && !profiles.contains("default")) {
            return;
        }
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableMicrcSupport.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("basePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }
        MessageMockSenderRouteTemplateParameterSource source =
                new MessageMockSenderRouteTemplateParameterSource();

        // message mock sender scanner
        MessageMockSenderApiScanner messageMockSenderApiScanner =
                new MessageMockSenderApiScanner(registry, source);
        messageMockSenderApiScanner.setResourceLoader(resourceLoader);
        messageMockSenderApiScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<MessageMockSenderRouteTemplateParameterSource>) source.getClass(),
                        () -> source)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(beanDefinition, registry),
                beanDefinition);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

}

/**
 * 消息MOCK发送器扫描器，用于扫描@MessageAdapter注解
 * 获取注解中的声明逻辑的属性，构造路由模版源，最终注入camel context用于构造执行路由
 *
 * @author hyosunghan
 * @date 2022-11-19 14:16
 * @since 0.0.1
 */
class MessageMockSenderApiScanner extends ClassPathBeanDefinitionScanner {

    private static final AtomicInteger INDEX = new AtomicInteger();
    private final MessageMockSenderRouteTemplateParameterSource sourceDefinition;

    public MessageMockSenderApiScanner(BeanDefinitionRegistry registry,
                                       MessageMockSenderRouteTemplateParameterSource source) {
        super(registry, false);
        this.sourceDefinition = source;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isInterface() && metadata.isIndependent();
    }

    @SneakyThrows
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        this.addIncludeFilter(new AnnotationTypeFilter(MessageAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        StringBuilder path = new StringBuilder();
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            Class<?> listenerClass = beanDefinition.getBeanClass();
            String listenerName = listenerClass.getSimpleName();
            // 获取MessageAdapter注解参数
            MessageAdapter messageAdapter = listenerClass.getAnnotation(MessageAdapter.class);
            listenerClass.getDeclaringClass();
            String[] servicePathArray = messageAdapter.commandServicePath().split("\\.");
            String serviceName = servicePathArray[servicePathArray.length - 1];
            // 获取实现方法中的topic
            Class<?>[] innerClasses = listenerClass.getDeclaredClasses();
            Class<?> implClass = Arrays.stream(innerClasses)
                    .filter(innerClass -> (listenerName + "Impl").equals(innerClass.getSimpleName()))
                    .findFirst().orElseThrow();
            String topicName = Arrays.stream(implClass.getDeclaredMethods()).map(method -> {
                KafkaListener kafkaListener = method.getAnnotation(KafkaListener.class);
                return kafkaListener.topics()[0];
            }).findFirst().orElseThrow();
            MessageMockSenderRouteConfiguration.MessageMockSenderDefinition build = MessageMockSenderRouteConfiguration.MessageMockSenderDefinition.builder()
                    .templateId(MessageMockSenderRouteConfiguration.ROUTE_TMPL_MESSAGE_SENDER)
                    .listenerName(listenerName)
                    .eventName(messageAdapter.eventName())
                    .serviceName(serviceName)
                    .topicName(topicName)
                    .build();
            sourceDefinition.addParameter(routeId(listenerName),build);
            path.append("," +
                    "\"/" + listenerName + "-" + serviceName + "\": {\n" +
                            "      \"post\": {\n" +
                            "        \"operationId\": \"" + listenerName + "-" + serviceName + "\",\n" +
                            "        \"requestBody\": {\n" +
                            "          \"content\": {\n" +
                            "            \"application/json\": {\n" +
                            "              \"schema\": {\n" +
                            "              }\n" +
                            "            }\n" +
                            "          }\n" +
                            "        },\n" +
                            "        \"responses\": {\n" +
                            "          \"content\": {\n" +
                            "            \"application/json\": {\n" +
                            "              \"schema\": {\n" +
                            "              }\n" +
                            "            }\n" +
                            "          }\n" +
                            "        }\n" +
                            "      }\n" +
                            "    }\n");
        }
        String doc = "{\n" +
                "  \"openapi\" : \"3.0.3\",\n" +
                "  \"info\" : {\n" +
                "    \"version\" : \"1.0.0\"\n" +
                "  },\n" +
                "  \"servers\" : [ {\n" +
                "    \"url\" : \"http://localhost:8080/api\"\n" +
                "  } ],\n" +
                "  \"components\" : {" +
                "    \"securitySchemes\" : {\n" +
                "      \"apiKeyAuth\" : {\n" +
                "        \"type\" : \"apiKey\",\n" +
                "        \"name\" : \"Authorization\",\n" +
                "        \"in\" : \"header\"\n" +
                "      }\n" +
                "    }," +
                "  },\n" +
                "\"security\": [\n" +
                "    {\n" +
                "      \"apiKeyAuth\": []\n" +
                "    }\n" +
                "  ]," +
                "  \"paths\" : {\n" +
                (path.length() == 0 ? path.toString() : path.substring(1)) +
                "  }\n" +
                "}";
        RpcEnvironmentProcessor.APIDOCS.put(RpcEnvironmentProcessor.APIDOC_BASE_URI + RpcEnvironmentProcessor.MOCK_SENDER_URL, doc);
        holders.clear();
        return holders;
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
        // nothing to do. leave it out.
    }

    private String routeId(String id) {
        String routeId = id;
        if (!StringUtils.hasText(routeId)) {
            routeId = String.valueOf(INDEX.getAndIncrement());
        }
        return MessageMockSenderRouteConfiguration.ROUTE_TMPL_MESSAGE_SENDER + "-" + routeId;
    }
}
