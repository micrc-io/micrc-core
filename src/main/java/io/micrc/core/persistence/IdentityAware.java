package io.micrc.core.persistence;

/**
 * 所有实体Id值对象应该实现这个接口，由base repo使用为其设置主键ID
 *
 * @author weiguan
 * @date 2022-9-26 20:47
 * @since 0.0.1
 */
public interface IdentityAware {
    void setIdentity(long id);
}
