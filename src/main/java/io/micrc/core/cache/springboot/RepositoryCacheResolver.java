package io.micrc.core.cache.springboot;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.interceptor.CacheEvictOperation;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.SimpleCacheResolver;

import io.micrc.core.persistence.MicrcJpaRepository;

/**
 * 资源库缓存处理器，根据具体的资源库接口注解@CacheConfig配置的资源库缓存名称
 * 确定MicrcJpaRepository基础接口的各方法缓存名称
 * 其中findById，save，count和saveAndFlush使用独立缓存，名称追加-entity。类似于hibernate二级缓存的实体缓存
 * 其他所有方法，包括具体资源库接口的自定义方法都使用资源库缓存名称。类似于hibernate的查询缓存
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-24 04:31
 */
public class RepositoryCacheResolver extends SimpleCacheResolver {

    private static final Map<String, Integer> ENTITY_REPO_METHOD_MAP = new HashMap<>();

    static {
        ENTITY_REPO_METHOD_MAP.put("findById", 1);
        ENTITY_REPO_METHOD_MAP.put("save", 1);
        ENTITY_REPO_METHOD_MAP.put("count", 0);
        ENTITY_REPO_METHOD_MAP.put("saveAndFlush", 1);
    }

    public RepositoryCacheResolver(CacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    protected Collection<String> getCacheNames(CacheOperationInvocationContext<?> context) {
        Collection<String> cacheNames = super.getCacheNames(context);
        if (!cacheNames.isEmpty()) {
            return cacheNames;
        }
        Class<?>[] interfaces = context.getTarget().getClass().getInterfaces();
        if (interfaces.length < 1) {
            return Collections.emptyList();
        }
        CacheConfig cacheConfig = null;
        for (Class<?> clazz : interfaces) {
            Class<?>[] parentInterfaces = clazz.getInterfaces();
            if (parentInterfaces.length == 1 && parentInterfaces[0].isAssignableFrom(MicrcJpaRepository.class)) {
                cacheConfig = clazz.getAnnotation(CacheConfig.class);
                break;
            }
        }
        if (cacheConfig == null) {
            return Collections.emptyList();
        }
        cacheNames = Arrays.asList(cacheConfig.cacheNames());
        Method method = context.getMethod();
        if (ENTITY_REPO_METHOD_MAP.containsKey(method.getName())
                && ENTITY_REPO_METHOD_MAP.get(method.getName()).equals(method.getParameterCount())) {
            if (context.getOperation() instanceof CacheEvictOperation
                && ((CacheEvictOperation) context.getOperation()).isCacheWide()) {
                return cacheNames;
            }
            cacheNames = cacheNames.stream().map(item -> item + ":entity").collect(Collectors.toList());
        }
        return cacheNames;
    }
    
}
