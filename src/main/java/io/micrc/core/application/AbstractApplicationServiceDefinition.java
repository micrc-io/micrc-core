package io.micrc.core.application;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * 应用服务路由参数定义抽象类
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-23 21:02
 */
@Data
@SuperBuilder
public abstract class AbstractApplicationServiceDefinition {
    protected String templateId;
}
