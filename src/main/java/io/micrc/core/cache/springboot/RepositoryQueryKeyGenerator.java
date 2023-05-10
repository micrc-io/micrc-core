package io.micrc.core.cache.springboot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.cache.interceptor.SimpleKeyGenerator;

/**
 * 资源库查询方法缓存key生成器
 * 除save，saveAndFlush，count，findById方法外的所有资源库接口，包括MicrcJpaRepository中的和具体资源库接口中的具体查询方法
 * 规则为使用方法名和所有方法参数值作为key
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-25 15:41
 */
public class RepositoryQueryKeyGenerator extends SimpleKeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        List<Object> paramList = new ArrayList<>();
        paramList.add(method.getName());
        paramList.addAll(Arrays.asList(params));
        return super.generate(target, method, paramList.toArray());
    }
}
