package io.micrc.core.message.springboot;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.message.DomainEvents;
import io.micrc.core.message.MessageRouteConfiguration;
import io.micrc.core.message.MessageRouteConfiguration.EventsInfo;
import io.micrc.core.message.MessageRouteConfiguration.EventsInfo.Event;
import io.micrc.lib.FileUtils;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息监听适配器扫描，注入路由参数bean，用于每个适配器生成路由
 *
 * @author weiguan
 * @date 2022-09-13 05:30
 * @since 0.0.1
 */
public class ClassPathMessageScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableMicrcSupport.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("basePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata).getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        MessageRouteConfiguration.EventsInfo eventsInfo = new EventsInfo();
        /**
         * 发送器注解扫描
         */
        MessagePublisherScanner messagePublisherScanner = new MessagePublisherScanner(registry, eventsInfo);
        messagePublisherScanner.setResourceLoader(resourceLoader);
        messagePublisherScanner.doScan(basePackages);

        // registering
        @SuppressWarnings("unchecked")
        BeanDefinition eventsInfoBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition((Class<EventsInfo>) eventsInfo.getClass(), () -> eventsInfo).getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(eventsInfoBeanDefinition, registry), eventsInfoBeanDefinition);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}

class MessagePublisherScanner extends ClassPathBeanDefinitionScanner {

    private final EventsInfo eventsInfo;

    public MessagePublisherScanner(BeanDefinitionRegistry registry, EventsInfo eventsInfo) {
        super(registry, false);
        this.eventsInfo = eventsInfo;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isInterface() && metadata.isIndependent();
    }

    @SneakyThrows
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        this.addIncludeFilter(new AnnotationTypeFilter(DomainEvents.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            String serviceName = beanDefinition.getBeanClass().getSimpleName();
            DomainEvents domainEvents = beanDefinition.getBeanClass().getAnnotation(DomainEvents.class);
            Arrays.stream(domainEvents.events()).forEach(eventInfo -> {
                // 事件对应接收方各自映射
                List<EventsInfo.EventMapping> mappingList = Arrays.stream(eventInfo.mappings())
                        .map(mapping -> EventsInfo.EventMapping.builder()
                                .mappingKey(mapping.mappingKey())
                                .mappingPath(FileUtils.fileReader(mapping.mappingPath(), List.of("jslt")))
                                .receiverAddress(mapping.receiverAddress())
                                .batchModel(mapping.batchModel())
                                .build())
                        .collect(Collectors.toList());
                // 没有接收方的事件不扫描
                if (mappingList.isEmpty()) {
                    return;
                }
                // 事件信息
                Event event = Event.builder()
                        .topicName(eventInfo.topicName())
                        .eventName(eventInfo.eventName())
                        .eventMappings(mappingList)
                        .build();
                // 注册事件信息
                eventsInfo.put(serviceName + "-" + event.getTopicName(), event);
            });
        }
        holders.clear();
        return holders;
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry beanDefinitionRegistry) {
        // nothing to do.
    }
}