package io.micrc.core.integration.derivations.springboot;

import io.micrc.core.annotations.integration.derivation.DerivationsAdapter;
import io.micrc.core.integration.derivations.DerivationsAdapterRouteConfiguration;
import io.micrc.core.integration.derivations.DerivationsAdapterRouteTemplateParameterSource;
import io.micrc.core.integration.derivations.EnableDerivationsAdapter;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 衍生适配器路由参数源bean注册器
 *
 * @author hyosunghan
 * @date 2022-09-21 13:48
 * @since 0.0.1
 */
@Component
public class ClassPathDerivationsAdapterScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableDerivationsAdapter.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        DerivationsAdapterRouteTemplateParameterSource source =
                new DerivationsAdapterRouteTemplateParameterSource();

        // application derivations service scanner
        ApplicationDerivationsAdapterScanner applicationDerivationsAdapterScanner =
                new ApplicationDerivationsAdapterScanner(registry, source);
        applicationDerivationsAdapterScanner.setResourceLoader(resourceLoader);
        applicationDerivationsAdapterScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<DerivationsAdapterRouteTemplateParameterSource>) source.getClass(),
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
 * @author hyosunghan
 * @date 2022-09-21 14:02
 * @since 0.0.1
 */
class ApplicationDerivationsAdapterScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final DerivationsAdapterRouteTemplateParameterSource sourceDefinition;

    public ApplicationDerivationsAdapterScanner(BeanDefinitionRegistry registry,
                                                  DerivationsAdapterRouteTemplateParameterSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(DerivationsAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            String name = beanDefinition.getBeanClass().getSimpleName();
            Method[] adapterMethods = beanDefinition.getBeanClass().getDeclaredMethods();
            Boolean haveAdaptMethod = Arrays.stream(adapterMethods).anyMatch(method -> "adapt".equals(method.getName()));
            if (!haveAdaptMethod || adapterMethods.length != 1) {
                throw new IllegalStateException(" the message adapter interface " + name + " need extends MessageIntegrationAdapter. please check");
            }
            DerivationsAdapter derivationsAdapter = beanDefinition.getBeanClass().getAnnotation(DerivationsAdapter.class);
            // 需要自定义的服务不生成路由
            if (derivationsAdapter.custom()) {
                continue;
            }
            String serviceName = derivationsAdapter.serviceName();
//            String servicePath = basePackages[0] + ".application.derivations.store." + serviceName;
//            Class<?> service = Class.forName(servicePath);
//            if (null == service) {
//                throw new ClassNotFoundException(" the application service interface " + servicePath + " not exist. please check");
//            }
//
//            Method[] serviceMethods = service.getDeclaredMethods();
//            List<Method> haveExecuteMethod = Arrays.stream(serviceMethods).filter(method -> "execute".equals(method.getName()) && !method.isBridge()).collect(Collectors.toList());
//            if (haveExecuteMethod.size() != 1) {
//                throw new IllegalStateException(" the application service interface " + serviceName + " need extends ApplicationBusinessesService. please check");
//            }
//            Class<?>[] serviceMethodParameterTypes = serviceMethods[0].getParameterTypes();
//            if (serviceMethodParameterTypes.length != 1) {
//                throw new IllegalStateException(" the message endpoint service interface " + serviceName + " method execute param only can use command and only one param. please check");
//            }
            sourceDefinition.addParameter(
                    routeId(serviceName),
                    DerivationsAdapterRouteConfiguration.ApplicationDerivationsAdapterDefinition.builder()
                            .templateId(DerivationsAdapterRouteConfiguration.ROUTE_TMPL_DERIVATIONS_ADAPTER)
                            .name(name)
                            .serviceName(serviceName)
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
        return DerivationsAdapterRouteConfiguration.ROUTE_TMPL_DERIVATIONS_ADAPTER + "-" + routeId;
    }

}
