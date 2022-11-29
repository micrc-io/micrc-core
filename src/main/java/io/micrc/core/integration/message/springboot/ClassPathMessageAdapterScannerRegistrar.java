package io.micrc.core.integration.message.springboot;

import io.micrc.core.annotations.message.rabbit.RabbitMessageAdapter;
import io.micrc.core.integration.message.EnableMessageAdapter;
import io.micrc.core.integration.message.MessageAdapterRouteConfiguration;
import io.micrc.core.integration.message.MessageAdapterRouteConfiguration.ApplicationMessageRouteTemplateParamDefinition;
import io.micrc.core.integration.message.MessageAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.message.MethodAdapterDesignException;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 消息接收适配器路由参数源bean注册器
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
@Component
public class ClassPathMessageAdapterScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableMessageAdapter.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        MessageAdapterRouteTemplateParameterSource source = new MessageAdapterRouteTemplateParameterSource();

        // application business service scanner
        ApplicationMessageAdapterScanner applicationMessageAdapterScanner = new ApplicationMessageAdapterScanner(
                registry, source);
        applicationMessageAdapterScanner.setResourceLoader(resourceLoader);
        applicationMessageAdapterScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<MessageAdapterRouteTemplateParameterSource>) source.getClass(),
                        () -> source)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(beanDefinition, registry),
                beanDefinition);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

}

/**
 * 消息接收适配注解扫描器
 *
 * @author weiguan
 * @date 2022-08-23 21:02
 * @since 0.0.1
 */
class ApplicationMessageAdapterScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final MessageAdapterRouteTemplateParameterSource sourceDefinition;

    public ApplicationMessageAdapterScanner(BeanDefinitionRegistry registry,
                                            MessageAdapterRouteTemplateParameterSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(RabbitMessageAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            if (beanDefinition.getBeanClass().getAnnotation(RabbitMessageAdapter.class).custom()) {
                continue;
            }
            String name = beanDefinition.getBeanClass().getSimpleName();
            Method[] adapterMethods = beanDefinition.getBeanClass().getDeclaredMethods();
            Boolean haveAdaptMethod = Arrays.stream(adapterMethods).anyMatch(method -> {
                if ("adapt".equals(method.getName())) {
                    return true;
                }
                return false;
            });
            if (!haveAdaptMethod || adapterMethods.length != 1) {
                throw new MethodAdapterDesignException(" the message adapter interface " + name
                        + " need extends MessageIntegrationAdapter. please check");
            }
            RabbitMessageAdapter messageAdapter = beanDefinition.getBeanClass().getAnnotation(RabbitMessageAdapter.class);
            String serviceName = messageAdapter.logicName() + "Service";
            String servicePath = basePackages[0] + ".application.businesses."
                    + messageAdapter.rootEntityName().toLowerCase() + "." + serviceName;
            Class<?> service = Class.forName(servicePath);
            if (null == service) {
                throw new ClassNotFoundException(
                        " the application service interface " + servicePath + " not exist. please check");
            }

            Method[] serviceMethods = service.getDeclaredMethods();
            List<Method> haveExecuteMethod = Arrays.stream(serviceMethods)
                    .filter(method -> "execute".equals(method.getName()) && !method.isBridge())
                    .collect(Collectors.toList());
            if (haveExecuteMethod.size() != 1) {
                throw new MethodAdapterDesignException(" the application service interface " + serviceName
                        + " need extends ApplicationBusinessesService. please check");
            }
            Class<?>[] serviceMethodParameterTypes = serviceMethods[0].getParameterTypes();
            if (serviceMethodParameterTypes.length != 1) {
                throw new MethodAdapterDesignException(" the message endpoint service interface " + serviceName
                        + " method execute param only can use command and only one param. please check");
            }
            String commandPath = serviceMethodParameterTypes[0].getName();

            sourceDefinition.addParameter(
                    routeId(serviceName),
                    ApplicationMessageRouteTemplateParamDefinition.builder()
                            .templateId(MessageAdapterRouteConfiguration.ROUTE_TMPL_MESSAGE)
                            .name(name)
                            .commandPath(commandPath)
                            .serviceName(serviceName)
                            .event(messageAdapter.eventName())
                            .ordered(messageAdapter.ordered())
                            .receiveEntityName(messageAdapter.rootEntityName())
                            .build());
        }
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
        return MessageAdapterRouteConfiguration.ROUTE_TMPL_MESSAGE + "-" + routeId;
    }

}
