package io.micrc.core.application.businesses;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 命令对象参数集成信息
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class CommandParamIntegration {

    /**
     * 属性名称-用来patch回原始CommandJson中  - 内部获取
     */
    private String paramName;

    /**
     * 参数所在对象图(Patch回去用,以/分割) - 注解输入
     */
    private String objectTreePath;

    /**
     * openApi集成协议 - 注解输入
     */
    private String protocol;

    /**
     * 该参数是否已经集成
     */
    private Boolean integrationComplete = false;

    public CommandParamIntegration(String paramName, String objectTreePath, String protocol) {
        this.paramName = paramName;
        this.objectTreePath = objectTreePath;
        this.protocol = protocol;
    }
}
