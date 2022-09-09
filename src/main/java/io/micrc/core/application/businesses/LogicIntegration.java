package io.micrc.core.application.businesses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 逻辑集成信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class LogicIntegration {

    /**
     * 出集成映射(调用时转换映射) jsonPath
     */
    private Map<String, String> outMappings;

    /**
     * 入集成映射(返回时转换映射)-转Target的 以target为根端点 PATCH
     */
    private Map<String, String> enterMappings;
}
