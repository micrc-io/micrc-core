package io.micrc.core.application.derivations.springboot;

import io.micrc.core.annotations.application.TimeParam;
import io.micrc.core.annotations.application.derivations.DerivationsService;
import io.micrc.core.annotations.application.derivations.GeneralTechnology;
import io.micrc.core.annotations.application.derivations.QueryLogic;
import io.micrc.core.annotations.application.derivations.SpecialTechnology;
import io.micrc.core.application.derivations.ApplicationDerivationsServiceRouteConfiguration;
import io.micrc.core.application.derivations.ApplicationDerivationsServiceRouteConfiguration.ApplicationDerivationsServiceDefinition;
import io.micrc.core.application.derivations.ApplicationDerivationsServiceRouteTemplateParameterSource;
import io.micrc.core.application.derivations.EnableDerivationsService;
import io.micrc.core.application.derivations.ParamIntegration;
import io.micrc.lib.FileUtils;
import io.micrc.lib.JsonUtil;
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
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 衍生服务路由参数源bean注册器
 *
 * @author hyosunghan
 * @date 2022-9-17 13:04
 * @since 0.0.1
 */
@Component
public class ClassPathDerivationsServiceScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @SuppressWarnings("unchecked")
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableDerivationsService.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata)
                    .getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        ApplicationDerivationsServiceRouteTemplateParameterSource source =
                new ApplicationDerivationsServiceRouteTemplateParameterSource();

        // application derivations service scanner
        ApplicationDerivationsServiceScanner applicationDerivationsServiceScanner =
                new ApplicationDerivationsServiceScanner(registry, source);
        applicationDerivationsServiceScanner.setResourceLoader(resourceLoader);
        applicationDerivationsServiceScanner.doScan(basePackages);

        // registering
        BeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(
                        (Class<ApplicationDerivationsServiceRouteTemplateParameterSource>) source.getClass(),
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
 * 应用衍生服务注解扫描器，用于扫描@DevirationsService及其execute方法的参数类型和属性的注解
 * 获取注解中的声明逻辑的属性，构造路由模版源，最终注入camel context用于构造执行路由
 *
 * @author hyosunghan
 * @date 2022-9-17 13:14
 * @since 0.0.1
 */
class ApplicationDerivationsServiceScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final ApplicationDerivationsServiceRouteTemplateParameterSource sourceDefinition;

    public ApplicationDerivationsServiceScanner(BeanDefinitionRegistry registry,
                                                  ApplicationDerivationsServiceRouteTemplateParameterSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(DerivationsService.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            // 检查方法数量，必须是单方法并且名称为"execute"
            List<Method> executeMethods = Arrays.stream(beanDefinition.getBeanClass().getMethods())
                    .filter(method -> method.getName().equals("execute")).collect(Collectors.toList());
            if (executeMethods.size() != 1) {
                throw new IllegalStateException("the " + beanDefinition.getBeanClass().getName() + " don`t have only one execute method, please check this application service....");
            }
            // 检查参数数量，必须是单参数
            Parameter[] parameters = executeMethods.get(0).getParameters();
            if (parameters.length != 1) {
                throw new IllegalStateException("the " + beanDefinition.getBeanClass().getName() + " execute method don`t have only one command param, please check this application service....");
            }
            DerivationsService derivationsService = beanDefinition.getBeanClass().getAnnotation(DerivationsService.class);
            // 需要自定义的服务不生成路由
            if (derivationsService.custom()) {
                continue;
            }
            // 解析参数所有集成
            List<ParamIntegration> paramIntegrations = getParamIntegrations(
                    derivationsService.queryLogics(),
                    derivationsService.generalTechnologies(),
                    derivationsService.specialTechnologies());
            // 获取明确的时间路径，并查找所有标记MicrcTime的路径
            ArrayList<String> timePaths = new ArrayList<>();
            TimeParam timeParam = beanDefinition.getBeanClass().getAnnotation(TimeParam.class);
            if (null != timeParam) {
                timePaths.addAll(Arrays.asList(timeParam.paths()));
            }
            // 检查衍生服务注解至少需要包含一次集成/查询
            if (paramIntegrations.isEmpty()) {
                throw new IllegalStateException("the " + beanDefinition.getBeanClass().getName() + " annotation should be at least one integration or query");
            }
            // 获取ApplicationService注解参数
            String serviceName = beanDefinition.getBeanClass().getSimpleName();
            sourceDefinition.addParameter(
                    routeId(serviceName),
                    ApplicationDerivationsServiceDefinition.builder()
                            .templateId(ApplicationDerivationsServiceRouteConfiguration.ROUTE_TMPL_DERIVATIONS_SERVICE)
                            .serviceName(serviceName)
                            .paramIntegrationsJson(JsonUtil.writeValueAsString(paramIntegrations))
                            .assembler(FileUtils.fileReader(derivationsService.assembler(), List.of("jslt")))
                            .timePathsJson(JsonUtil.writeValueAsString(timePaths))
                            .build());
        }
        holders.clear();
        return holders;
    }

    /**
     * 获取参数集成，包括查询逻辑和运算
     *
     * @param queryLogics
     * @param generalTechnologies
     * @param specialTechnologies
     * @return
     */
    private List<ParamIntegration> getParamIntegrations(QueryLogic[] queryLogics, GeneralTechnology[] generalTechnologies, SpecialTechnology[] specialTechnologies) {
        List<ParamIntegration> paramIntegrations = new ArrayList<>();
        // 查询逻辑解析
        Arrays.stream(queryLogics).forEach(logic -> {
            List<String> paramMappings = Arrays.stream(logic.paramMappingFile()).map(file -> FileUtils.fileReaderSingleLine(file, List.of("jslt"))).collect(Collectors.toList());
            paramIntegrations.add(new ParamIntegration(logic.name(), logic.repositoryFullClassName(), logic.methodName(), paramMappings, logic.order()));
        });
        // 专用技术解析
        Arrays.stream(specialTechnologies).forEach(t -> {
            String mapping = FileUtils.fileReaderSingleLine(t.paramMappingFile(), List.of("jslt"));
            String varMapping = StringUtils.hasText(t.variableMappingFile()) ? FileUtils.fileReaderSingleLine(t.variableMappingFile(), List.of("jslt")) : "{}";
            paramIntegrations.add(new ParamIntegration(t.name(),
                    t.scriptContentPath(), t.scriptFilePath(), varMapping, List.of(mapping),
                    t.order(), ParamIntegration.Type.SPECIAL_TECHNOLOGY, t.technologyType()));
        });
        // 通用技术解析
        Arrays.stream(generalTechnologies).forEach(t -> {
            String mapping = FileUtils.fileReaderSingleLine(t.paramMappingFile(), List.of("jslt"));
            String varMapping = StringUtils.hasText(t.variableMappingFile()) ? FileUtils.fileReaderSingleLine(t.variableMappingFile(), List.of("jslt")) : "{}";
            paramIntegrations.add(new ParamIntegration(t.name(),
                    t.routeContentPath(), t.routeXmlFilePath(), varMapping, List.of(mapping),
                    t.order(), ParamIntegration.Type.GENERAL_TECHNOLOGY, null));
        });
        // 按照优先级排序
        paramIntegrations.sort(Comparator.comparing(ParamIntegration::getOrder));
        return paramIntegrations;
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
        return ApplicationDerivationsServiceRouteConfiguration.ROUTE_TMPL_DERIVATIONS_SERVICE + "-" + routeId;
    }
}
