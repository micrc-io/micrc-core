package io.micrc.core.rpc.springboot;

import io.micrc.core.annotations.integration.command.CommandAdapter;
import io.micrc.core.annotations.integration.derivation.DerivationsAdapter;
import io.micrc.core.annotations.integration.presentations.PresentationsAdapter;
import io.micrc.core.framework.json.JsonUtil;
import io.micrc.core.message.EnableMessage;
import io.micrc.core.rpc.RpcCommandRestRouteParamSource;
import io.micrc.core.rpc.RpcDerivationRestRouteParamSource;
import io.micrc.core.rpc.RpcPresentationRestRouteParamSource;
import io.micrc.core.rpc.RpcRestRouteConfiguration;
import io.micrc.core.rpc.RpcRestRouteConfiguration.AdaptersInfo;
import io.micrc.core.rpc.RpcRestRouteConfiguration.RpcDefinition;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.HashMap;
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

    public static String fileReader(String filePath) throws FileNotFoundException {
        StringBuffer fileContent = new StringBuffer();
        try {
            InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(filePath);
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String str = null;
            while ((str = in.readLine()) != null) {
                fileContent.append(str);
            }
            in.close();
        } catch (IOException e) {
            throw new FileNotFoundException("the openapi protocol file not found...");
        }
        return fileContent.toString();
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableMessage.class.getName()));
        assert attributes != null;
        String[] basePackages = attributes.getStringArray("servicePackages");
        if (basePackages.length == 0 && importingClassMetadata instanceof StandardAnnotationMetadata) {
            basePackages = new String[]{((StandardAnnotationMetadata) importingClassMetadata).getIntrospectedClass().getPackage().getName()};
        }
        if (basePackages.length == 0) {
            return;
        }

        // 业务适配器注解扫描
        RpcCommandRestRouteParamSource rpcCommandScannerSource = new RpcCommandRestRouteParamSource();
        RpcCommandScanner rpcCommandScanner = new RpcCommandScanner(registry, rpcCommandScannerSource);
        rpcCommandScanner.setResourceLoader(resourceLoader);
        rpcCommandScanner.doScan(basePackages);

        BeanDefinition rpcCommandScannerSourceBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition((Class<RpcCommandRestRouteParamSource>) rpcCommandScannerSource.getClass(), () -> rpcCommandScannerSource).getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(rpcCommandScannerSourceBeanDefinition, registry), rpcCommandScannerSourceBeanDefinition);


        // 衍生适配器注解扫描
        RpcDerivationRestRouteParamSource rpcDerivationScannerSource = new RpcDerivationRestRouteParamSource();
        RpcDerivationScanner rpcDerivationScanner = new RpcDerivationScanner(registry, rpcDerivationScannerSource);
        rpcDerivationScanner.setResourceLoader(resourceLoader);
        rpcDerivationScanner.doScan(basePackages);

        BeanDefinition rpcDerivationScannerSourceBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition((Class<RpcDerivationRestRouteParamSource>) rpcDerivationScannerSource.getClass(), () -> rpcDerivationScannerSource).getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(rpcDerivationScannerSourceBeanDefinition, registry), rpcDerivationScannerSourceBeanDefinition);

        // 展示查询适配器注解扫描
        RpcPresentationRestRouteParamSource rpcPresentationScannerSource = new RpcPresentationRestRouteParamSource();
        RpcPresentationScanner rpcPresentationScanner = new RpcPresentationScanner(registry, rpcPresentationScannerSource);
        rpcPresentationScanner.setResourceLoader(resourceLoader);
        rpcPresentationScanner.doScan(basePackages);

        BeanDefinition rpcPresentationScannerBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition((Class<RpcPresentationRestRouteParamSource>) rpcPresentationScannerSource.getClass(), () -> rpcPresentationScannerSource).getRawBeanDefinition();
        registry.registerBeanDefinition(importBeanNameGenerator.generateBeanName(rpcPresentationScannerBeanDefinition, registry), rpcPresentationScannerBeanDefinition);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}

class RpcCommandScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RpcCommandRestRouteParamSource sourceDefinition;

    public RpcCommandScanner(BeanDefinitionRegistry registry, RpcCommandRestRouteParamSource source) {
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
        this.addIncludeFilter(new AnnotationTypeFilter(CommandAdapter.class));
        Set<BeanDefinitionHolder> holders = super.doScan(basePackages);
        AdaptersInfo adaptersInfo = new AdaptersInfo();
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
            String openapiProtocolJson = ClassPathRestEndpointScannerRegistrar.fileReader(protocolPath);
            Map pathsMap = JsonUtil.writeValueAsObject(JsonUtil.readTree(openapiProtocolJson).at("/paths").toString(), HashMap.class);
            if (1 != pathsMap.keySet().size()) {
                throw new IllegalArgumentException("the " + adapterName + " openapi protocol have error, please check....");
            }
            HashMap methodMap = (HashMap) pathsMap.values().stream().toArray()[0];
            String path = (String) pathsMap.keySet().toArray()[0];
            String methodName = (String) methodMap.keySet().toArray()[0];
            RpcDefinition rpcDefinition = RpcDefinition.builder()
                    .adapterName(adapterName)
                    .protocolPath(protocolPath)
                    .method(methodName)
                    .address(path)
                    .routeProtocol(routeProtocol)
                    .templateId(RpcRestRouteConfiguration.ROUTE_TMPL_REST_COMMAND)
                    .build();
            adaptersInfo.put(adapterName, rpcDefinition);
            sourceDefinition.addParameter(
                    routeId(adapterName + "-" + path + "-" + methodName),
                    rpcDefinition
            );
        }
        this.registBean(adaptersInfo);
        holders.clear();
        return holders;
    }

    private void registBean(Object beanInstance) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanInstance);
        beanDefinition.setBeanClass(AdaptersInfo.class);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
        beanDefinition.setLazyInit(false);
        beanDefinition.setPrimary(true);
        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, "commandAdaptersInfo");
        super.registerBeanDefinition(definitionHolder, super.getRegistry());
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
        return RpcRestRouteConfiguration.ROUTE_TMPL_REST_COMMAND + "-" + routeId;
    }
}

class RpcDerivationScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RpcDerivationRestRouteParamSource sourceDefinition;

    public RpcDerivationScanner(BeanDefinitionRegistry registry, RpcDerivationRestRouteParamSource source) {
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
            DerivationsAdapter derivationsAdapter = beanDefinition.getBeanClass().getAnnotation(DerivationsAdapter.class);
            if (derivationsAdapter.custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            String protocolPath = derivationsAdapter.protocolPath();
            String routeProtocol = derivationsAdapter.routeProtocol();
            String openapiProtocolJson = ClassPathRestEndpointScannerRegistrar.fileReader(protocolPath);
            Map pathsMap = JsonUtil.writeValueAsObject(JsonUtil.readTree(openapiProtocolJson).at("/paths").toString(), HashMap.class);
            if (1 != pathsMap.keySet().size()) {
                throw new IllegalArgumentException("the " + adapterName + " openapi protocol have error, please check....");
            }
            HashMap methodMap = (HashMap) pathsMap.values().stream().toArray()[0];
            String path = (String) pathsMap.keySet().toArray()[0];
            String methodName = (String) methodMap.keySet().toArray()[0];
            RpcDefinition rpcDefinition = RpcDefinition.builder()
                    .adapterName(adapterName)
                    .protocolPath(protocolPath)
                    .method(methodName)
                    .address(path)
                    .routeProtocol(routeProtocol)
                    .templateId(RpcRestRouteConfiguration.ROUTE_TMPL_REST_DERIVATION)
                    .build();
            adaptersInfo.put(adapterName, rpcDefinition);
            sourceDefinition.addParameter(
                    routeId(adapterName + "-" + path + "-" + methodName),
                    rpcDefinition
            );
        }
        this.registBean(adaptersInfo);
        holders.clear();
        return holders;
    }

    private void registBean(Object beanInstance) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanInstance);
        beanDefinition.setBeanClass(AdaptersInfo.class);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
        beanDefinition.setLazyInit(false);
        beanDefinition.setPrimary(true);
        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, "derivationsAdaptersInfo");
        super.registerBeanDefinition(definitionHolder, super.getRegistry());
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
        return RpcRestRouteConfiguration.ROUTE_TMPL_REST_DERIVATION + "-" + routeId;
    }
}

class RpcPresentationScanner extends ClassPathBeanDefinitionScanner {
    private static final AtomicInteger INDEX = new AtomicInteger();
    private final RpcPresentationRestRouteParamSource sourceDefinition;

    public RpcPresentationScanner(BeanDefinitionRegistry registry, RpcPresentationRestRouteParamSource source) {
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
            PresentationsAdapter presentationsAdapter = beanDefinition.getBeanClass().getAnnotation(PresentationsAdapter.class);
            if (presentationsAdapter.custom()) {
                continue;
            }
            String adapterName = beanDefinition.getBeanClass().getSimpleName();
            String protocolPath = presentationsAdapter.protocolPath();
            String routeProtocol = presentationsAdapter.routeProtocol();
            String openapiProtocolJson = ClassPathRestEndpointScannerRegistrar.fileReader(protocolPath);
            Map pathsMap = JsonUtil.writeValueAsObject(JsonUtil.readTree(openapiProtocolJson).at("/paths").toString(), HashMap.class);
            if (1 != pathsMap.keySet().size()) {
                throw new IllegalArgumentException("the " + adapterName + " openapi protocol have error, please check....");
            }
            HashMap methodMap = (HashMap) pathsMap.values().stream().toArray()[0];
            String path = (String) pathsMap.keySet().toArray()[0];
            String methodName = (String) methodMap.keySet().toArray()[0];
            RpcDefinition rpcDefinition = RpcDefinition.builder()
                    .adapterName(adapterName)
                    .protocolPath(protocolPath)
                    .method(methodName)
                    .address(path)
                    .routeProtocol(routeProtocol)
                    .templateId(RpcRestRouteConfiguration.ROUTE_TMPL_REST_PRESENTATION)
                    .build();
            adaptersInfo.put(adapterName, rpcDefinition);
            sourceDefinition.addParameter(
                    routeId(adapterName + "-" + path + "-" + methodName),
                    rpcDefinition
            );
        }
        this.registBean(adaptersInfo);
        holders.clear();
        return holders;
    }

    private void registBean(Object beanInstance) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setBeanClass(AdaptersInfo.class);
        beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanInstance);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
        beanDefinition.setLazyInit(false);
        beanDefinition.setPrimary(true);
        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, "presentationsAdaptersInfo");
        super.registerBeanDefinition(definitionHolder, super.getRegistry());
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
        return RpcRestRouteConfiguration.ROUTE_TMPL_REST_PRESENTATION + "-" + routeId;
    }
}