package io.micrc.core.application.businesses.springboot;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

import io.micrc.core.annotations.BusinessesService;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteTemplateParameterSource;
import io.micrc.core.application.businesses.EnableBusinessesService;
import io.micrc.core.application.businesses.ApplicationBusinessesServiceRouteConfiguration.ApplicationBusinessesServiceDefinition;
import lombok.SneakyThrows;

@Component
public class ClassPathBusinessesServiceScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableBusinessesService.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }
        
        ApplicationBusinessesServiceRouteTemplateParameterSource source =
                new ApplicationBusinessesServiceRouteTemplateParameterSource();

        // application business service scanner
        ApplicationBusinessesServiceScanner producerScanner =
                new ApplicationBusinessesServiceScanner(registry, source);
        producerScanner.setResourceLoader(resourceLoader);
        producerScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<ApplicationBusinessesServiceRouteTemplateParameterSource>) source.getClass(),
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

class ApplicationBusinessesServiceScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final ApplicationBusinessesServiceRouteTemplateParameterSource sourceDefinition;

    public ApplicationBusinessesServiceScanner(BeanDefinitionRegistry registry,
                                               ApplicationBusinessesServiceRouteTemplateParameterSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(BusinessesService.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            BusinessesService messageProducer =
                    beanDefinition.getBeanClass().getAnnotation(BusinessesService.class);
            // todo 还得获取ApplicationBusinessesService的exec方法的参数，获取它的参数 - command
            // todo command上及其属性上还有注解，从这些注解上获取属性
            // todo 填充到下面的ApplicationBusinessesServiceDefinition的builder中
            sourceDefinition.addParameter(
                    routeId(messageProducer),
                    ApplicationBusinessesServiceDefinition.builder()
                            .templateId(ApplicationBusinessesServiceRouteConfiguration.ROUTE_TMPL_BUSINESSES_SERVICE)
                            // todo 设置其他模版参数
                            .build());
        }
        holders.clear();
        return holders;
    }

    @Override
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry routersInfo) {
        // nothing to do. leave it out.
    }

    private String routeId(BusinessesService businessesService) {
        String routeId = businessesService.id();
        if (!StringUtils.hasText(routeId)) {
            routeId = String.valueOf(INDEX.getAndIncrement());
        }
        return ApplicationBusinessesServiceRouteConfiguration.ROUTE_TMPL_BUSINESSES_SERVICE + "-" + routeId;
    }
}
