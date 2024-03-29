package io.micrc.core.rpc.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.annotations.integration.command.CommandAdapter;
import io.micrc.core.annotations.integration.derivation.DerivationsAdapter;
import io.micrc.core.annotations.integration.presentations.PresentationsAdapter;
import io.micrc.core.rpc.RpcRestRouteConfiguration;
import io.micrc.core.rpc.RpcRestRouteConfiguration.AdaptersInfo;
import io.micrc.core.rpc.RpcRestRouteConfiguration.RpcDefinition;
import io.micrc.core.rpc.RpcRestRouteParamSource;
import io.micrc.lib.ClassCastUtils;
import io.micrc.lib.FileUtils;
import io.micrc.lib.JsonUtil;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;
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
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * rpc rest服务端点适配器扫描，注入路由参数bean，用于每个适配器生成路由
 *
 * @author weiguan
 * @date 2022-09-13 07:09
 * @since 0.0.1
 */
public class ClassPathRestEndpointScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableMicrcSupport.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("basePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata).getIntrospectedClass()
                    .getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        RpcRestRouteParamSource rpcRestRouteParamSource = new RpcRestRouteParamSource();
        AdaptersInfo adaptersInfo = new AdaptersInfo();
        // 业务适配器注解扫描
        RpcCommandScanner rpcCommandScanner = new RpcCommandScanner(registry, rpcRestRouteParamSource, adaptersInfo);
        rpcCommandScanner.setResourceLoader(resourceLoader);
        rpcCommandScanner.doScan(basePackages);

        // 衍生适配器注解扫描
        RpcDerivationScanner rpcDerivationScanner = new RpcDerivationScanner(registry, rpcRestRouteParamSource,
                adaptersInfo);
        rpcDerivationScanner.setResourceLoader(resourceLoader);
        rpcDerivationScanner.doScan(basePackages);

        // 展示查询适配器注解扫描
        RpcPresentationScanner rpcPresentationScanner = new RpcPresentationScanner(registry, rpcRestRouteParamSource,
                adaptersInfo);
        rpcPresentationScanner.setResourceLoader(resourceLoader);
        rpcPresentationScanner.doScan(basePackages);

        @SuppressWarnings("unchecked")
        BeanDefinition rpcPresentationScannerBeanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition((Class<RpcRestRouteParamSource>) rpcRestRouteParamSource.getClass(),
                        () -> rpcRestRouteParamSource)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(
                importBeanNameGenerator.generateBeanName(rpcPresentationScannerBeanDefinition, registry),
                rpcPresentationScannerBeanDefinition);

        @SuppressWarnings("unchecked")
        BeanDefinition adaptersInfoBeanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition((Class<AdaptersInfo>) adaptersInfo.getClass(), () -> adaptersInfo)
                .getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(adaptersInfoBeanDefinition, registry),
                adaptersInfoBeanDefinition);

    }

    @Nullable
    protected static Object getSubjectPath(Map<String, Object> pathsMap, String path, String methodName) {
        Object pathContent = pathsMap.get(path);
        Object content = JsonUtil.readPath(JsonUtil.writeValueAsString(pathContent), "/" + methodName + "/requestBody/content");
        Map<String, Object> stringObjectMap = ClassCastUtils.castHashMap(content, String.class, Object.class);
        if (stringObjectMap == null) {
            return null;
        }
        return JsonUtil.readPath(JsonUtil.writeValueAsString(stringObjectMap.values().toArray()[0]), "/x-subject-path");
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}

class RpcCommandScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RpcRestRouteParamSource sourceDefinition;
    private final AdaptersInfo adaptersInfo;

    public RpcCommandScanner(BeanDefinitionRegistry registry, RpcRestRouteParamSource source,
                             AdaptersInfo adaptersInfo) {
        super(registry, false);
        this.sourceDefinition = source;
        this.adaptersInfo = adaptersInfo;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isInterface() && metadata.isIndependent();
    }

    @SneakyThrows
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        this.addIncludeFilter(new AnnotationTypeFilter(CommandAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            CommandAdapter commandAdapter = beanDefinition.getBeanClass().getAnnotation(CommandAdapter.class);
            if (commandAdapter.custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            String protocolPath = commandAdapter.protocolPath();
            String routeProtocol = commandAdapter.routeProtocol();
            String openapiProtocolJson = FileUtils.fileReader(protocolPath, List.of("json"));
            JsonNode protocolNode = JsonUtil.readTree(openapiProtocolJson);
            JsonNode serversNode = protocolNode.at("/servers").get(0);
            String url = serversNode.at("/url").textValue();
            Map<String, Object> pathsMap = ClassCastUtils.castHashMap(
                    JsonUtil.writeValueAsObject(JsonUtil.readTree(openapiProtocolJson).at("/paths").toString(),
                            HashMap.class),
                    String.class,
                    Object.class);
            if (1 != pathsMap.keySet().size()) {
                throw new IllegalArgumentException(
                        "the " + adapterName + " openapi protocol have error, please check....");
            }
            Map<String, Object> methodMap = ClassCastUtils.castHashMap(pathsMap.values().stream().toArray()[0],
                    String.class, Object.class);
            String path = (String) pathsMap.keySet().toArray()[0];
            String methodName = (String) methodMap.keySet().toArray()[0];
            String xSubjectPath = (String) ClassPathRestEndpointScannerRegistrar.getSubjectPath(pathsMap, path, methodName);
            RpcDefinition rpcDefinition = RpcDefinition.builder()
                    .adapterName(adapterName)
                    .protocolPath(protocolPath)
                    .method(methodName)
                    .address(url + path)
                    .routeProtocol(routeProtocol)
                    .subjectPath(xSubjectPath == null ? "" : xSubjectPath)
                    .templateId(RpcRestRouteConfiguration.ROUTE_TMPL_REST)
                    .build();
            adaptersInfo.add(rpcDefinition);
            sourceDefinition.addParameter(
                    routeId(adapterName + "-" + path + "-" + methodName),
                    rpcDefinition);
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
        return RpcRestRouteConfiguration.ROUTE_TMPL_REST + "-" + routeId;
    }
}

class RpcDerivationScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RpcRestRouteParamSource sourceDefinition;

    public RpcDerivationScanner(BeanDefinitionRegistry registry, RpcRestRouteParamSource source,
                                AdaptersInfo adaptersInfo) {
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
        AdaptersInfo adaptersInfo = new AdaptersInfo();
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            DerivationsAdapter derivationsAdapter = beanDefinition.getBeanClass()
                    .getAnnotation(DerivationsAdapter.class);
            if (derivationsAdapter.custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            String protocolPath = derivationsAdapter.protocolPath();
            String routeProtocol = derivationsAdapter.routeProtocol();
            String openapiProtocolJson = FileUtils.fileReader(protocolPath, List.of("json"));
            JsonNode protocolNode = JsonUtil.readTree(openapiProtocolJson);
            JsonNode serversNode = protocolNode.at("/servers").get(0);
            String url = serversNode.at("/url").textValue();
            Map<String, Object> pathsMap = ClassCastUtils.castHashMap(
                    JsonUtil.writeValueAsObject(JsonUtil.readTree(openapiProtocolJson).at("/paths").toString(),
                            HashMap.class),
                    String.class,
                    Object.class);
            if (1 != pathsMap.keySet().size()) {
                throw new IllegalArgumentException(
                        "the " + adapterName + " openapi protocol have error, please check....");
            }
            Map<String, Object> methodMap = ClassCastUtils.castHashMap(pathsMap.values().stream().toArray()[0],
                    String.class, Object.class);
            String path = (String) pathsMap.keySet().toArray()[0];
            String methodName = (String) methodMap.keySet().toArray()[0];
            RpcDefinition rpcDefinition = RpcDefinition.builder()
                    .adapterName(adapterName)
                    .protocolPath(protocolPath)
                    .method(methodName)
                    .address(url + path)
                    .routeProtocol(routeProtocol)
                    .subjectPath("")
                    .templateId(RpcRestRouteConfiguration.ROUTE_TMPL_REST)
                    .build();
            adaptersInfo.add(rpcDefinition);
            sourceDefinition.addParameter(
                    routeId(adapterName + "-" + path + "-" + methodName),
                    rpcDefinition);
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
        return RpcRestRouteConfiguration.ROUTE_TMPL_REST + "-" + routeId;
    }
}

class RpcPresentationScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RpcRestRouteParamSource sourceDefinition;

    public RpcPresentationScanner(BeanDefinitionRegistry registry, RpcRestRouteParamSource source,
                                  AdaptersInfo adaptersInfo) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(PresentationsAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        AdaptersInfo adaptersInfo = new AdaptersInfo();
        for (BeanDefinitionHolder holder : holders) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            beanDefinition.resolveBeanClass(Thread.currentThread().getContextClassLoader());
            PresentationsAdapter presentationsAdapter = beanDefinition.getBeanClass()
                    .getAnnotation(PresentationsAdapter.class);
            if (presentationsAdapter.custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            String protocolPath = presentationsAdapter.protocolPath();
            String routeProtocol = presentationsAdapter.routeProtocol();
            String openapiProtocolJson = FileUtils.fileReader(protocolPath, List.of("json"));
            JsonNode protocolNode = JsonUtil.readTree(openapiProtocolJson);
            JsonNode serversNode = protocolNode.at("/servers").get(0);
            String url = serversNode.at("/url").textValue();
            Map<String, Object> pathsMap = ClassCastUtils.castHashMap(
                    JsonUtil.writeValueAsObject(JsonUtil.readTree(openapiProtocolJson).at("/paths").toString(),
                            HashMap.class),
                    String.class,
                    Object.class);
            if (1 != pathsMap.keySet().size()) {
                throw new IllegalArgumentException(
                        "the " + adapterName + " openapi protocol have error, please check....");
            }
            Map<String, Object> methodMap = ClassCastUtils.castHashMap(pathsMap.values().stream().toArray()[0],
                    String.class, Object.class);
            String path = (String) pathsMap.keySet().toArray()[0];
            String methodName = (String) methodMap.keySet().toArray()[0];
            String xSubjectPath = (String) ClassPathRestEndpointScannerRegistrar.getSubjectPath(pathsMap, path, methodName);
            RpcDefinition rpcDefinition = RpcDefinition.builder()
                    .adapterName(adapterName)
                    .protocolPath(protocolPath)
                    .method(methodName)
                    .address(url + path)
                    .routeProtocol(routeProtocol)
                    .subjectPath(xSubjectPath == null ? "" : xSubjectPath)
                    .templateId(RpcRestRouteConfiguration.ROUTE_TMPL_REST)
                    .build();
            adaptersInfo.add(rpcDefinition);
            sourceDefinition.addParameter(
                    routeId(adapterName + "-" + path + "-" + methodName),
                    rpcDefinition);
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
        return RpcRestRouteConfiguration.ROUTE_TMPL_REST + "-" + routeId;
    }
}