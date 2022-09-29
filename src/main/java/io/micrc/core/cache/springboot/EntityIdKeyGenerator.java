package io.micrc.core.cache.springboot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.util.ReflectionUtils;

import lombok.SneakyThrows;

/**
 * MicrcJpaRepository接口save和saveAndFlush方法使用@CachePut时，专用的缓存key生成器
 * 使用实体id作为缓存key，实体必须有@Entity注解，且有一个名为id并注解为@Id的属性或有一个名为embeddedIdentity并注解为@EmbeddedId且有名为id的属性，这个属性值作为实体的缓存key
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-25 09:31
 */
public class EntityIdKeyGenerator implements KeyGenerator {

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
        Field idField = ReflectionUtils.findField(entity.getClass(), "id");
        Field embeddedIdentityField = ReflectionUtils.findField(entity.getClass(), "embeddedIdentity");
        if (idField == null && embeddedIdentityField == null) {
            throw new IllegalArgumentException("Caching function: " + method.getName() + "'s param must have a id or embeddedIdentity field");
        }
        if (idField != null) {
            return idKeyGenerator(entity, method, idField);
        } else {
            return embeddedIdentityKeyGenerator(entity, method, embeddedIdentityField);
        }
    }

    private Object idKeyGenerator(Object target, Method method, Field idField) {
        ReflectionUtils.makeAccessible(idField);
        Object param = ReflectionUtils.getField(idField, target);
        if (param == null || idField.getAnnotation(Id.class) == null) {
            throw new IllegalArgumentException("Caching function: " + method.getName() + "'s param must have a @Id.");
        }
        return param;
    }

    private Object embeddedIdentityKeyGenerator(Object target, Method method, Field embeddedIdentityField) {
        ReflectionUtils.makeAccessible(embeddedIdentityField);
        Object param = ReflectionUtils.getField(embeddedIdentityField, target);
        if (param == null || embeddedIdentityField.getAnnotation(EmbeddedId.class) == null) {
            throw new IllegalArgumentException(
                "Caching function: " + method.getName() + "'s param must have a @EmbeddedId.");
        }
        Field idField = ReflectionUtils.findField(embeddedIdentityField.getType(), "id");
        if (idField == null) {
            throw new IllegalArgumentException(
                "Caching function: " + method.getName() + "'s param embeddedIdentity must have a id field");
        }
        ReflectionUtils.makeAccessible(idField);
        param = ReflectionUtils.getField(idField, param);
        if (param == null) {
            throw new IllegalArgumentException(
                "Caching function: " + method.getName() + "'s param embeddedIdentity must have a id value.");
        }
        return param;
    }
}
