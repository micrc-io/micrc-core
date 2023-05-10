package io.micrc.core;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * 路由参数定义抽象类
 *
 * @author tengwang
 * @date 2022-09-05 12:00
 * @since 0.0.1
 */
@Data
@SuperBuilder
public abstract class AbstractRouteTemplateParamDefinition {
    protected String templateId;
}
