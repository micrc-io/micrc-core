package io.micrc.core.cache.springboot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.persistence.Entity;
import javax.persistence.Id;

import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.util.ReflectionUtils;

import lombok.SneakyThrows;

/**
 * MicrcJpaRepository接口save和saveAndFlush方法使用@CachePut时，专用的缓存key生成器
 * 使用实体id作为缓存key，实体必须有@Entity注解，且有一个名为id并注解为@Id的属性，这个属性值作为实体的缓存key
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-25 09:31
 */
public class EntityIdKeyGenerator extends SimpleKeyGenerator {

    @SneakyThrows
    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length != 1) {
            throw new IllegalArgumentException("Caching function: " + method.getName() + " must have only one param.");
        }
        Object entity = params[0];
        Entity entityAnnotation = entity.getClass().getAnnotation(Entity.class);
        if (entityAnnotation == null) {
            throw new IllegalArgumentException("Caching function: " + method.getName() + "'s param must be @Entity.");
        }
        Object param = null;
        Field idField = ReflectionUtils.findField(entity.getClass(), "id");
        if (idField == null) {
            throw new IllegalArgumentException("Caching function: " + method.getName() + "'s param must have a id field");
        }
        ReflectionUtils.makeAccessible(idField);
        param = ReflectionUtils.getField(idField, entity);
        if (param == null || idField.getAnnotation(Id.class) == null) {
            throw new IllegalArgumentException("Caching function: " + method.getName() + "'s param must have a @Id.");
        }
        return super.generate(target, method, param);
    }
}
