package io.micrc.core.application.presentations;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 参数集成信息
 *
 * @author hyosunghan
 * @date 2022-9-12 11:02
 * @since 0.0.1
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class ParamIntegration {

    /**
     * 概念
     */
    private String concept;

    /**
     * 实体路径
     *
     * @return
     */
    private String entityPath;

    /**
     * 查询方法
     *
     * @return
     */
    private String queryMethod;

    /**
     * 参数映射
     */
    private List<String> paramMappings;

    /**
     * openApi集成协议 - 注解输入
     */
    private String protocol;

    /**
     * 请求映射文件
     *
     * @return
     */
    private String requestMapping;

    /**
     * 响应映射文件
     *
     * @return
     */
    private String responseMapping;

    /**
     * 该参数是否已经集成
     */
    private Boolean integrationComplete = false;

    /**
     * 执行顺序
     */
    private int order;

    /**
     * 集成类型
     */
    private Type type;

    public ParamIntegration(String concept, String protocol, String requestMapping, String responseMapping, int order) {
        this.concept = concept;
        this.protocol = protocol;
        this.order = order;
        this.requestMapping = requestMapping;
        this.responseMapping = responseMapping;
        this.type = Type.INTEGRATE;
    }

    public ParamIntegration(String concept, String entityPath, String queryMethod, List<String> paramMappings, int order) {
        this.concept = concept;
        this.entityPath = entityPath;
        this.queryMethod = queryMethod;
        this.paramMappings = paramMappings;
        this.order = order;
        this.responseMapping = ".";
        this.type = Type.QUERY;
    }

    /**
     * 类型
     */
    public enum Type {
        QUERY,
        INTEGRATE,
    }
}
