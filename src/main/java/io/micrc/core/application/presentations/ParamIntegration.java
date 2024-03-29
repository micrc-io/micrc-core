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
     * 仓库路径
     *
     * @return
     */
    private String repositoryPath;

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
     * 参数缺失则忽略此次集成
     */
    private boolean ignoreIfParamAbsent;

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

    public ParamIntegration(String concept, String protocol, String requestMapping, String responseMapping, int order, boolean ignoreIfParamAbsent) {
        this.concept = concept;
        this.protocol = protocol;
        this.order = order;
        this.requestMapping = requestMapping;
        this.responseMapping = responseMapping;
        this.ignoreIfParamAbsent = ignoreIfParamAbsent;
        this.type = Type.INTEGRATE;
    }

    public ParamIntegration(String concept, String repositoryPath, String queryMethod, List<String> paramMappings, int order, boolean ignoreIfParamAbsent) {
        this.concept = concept;
        this.repositoryPath = repositoryPath;
        this.queryMethod = queryMethod;
        this.paramMappings = paramMappings;
        this.order = order;
        this.responseMapping = ".";
        this.ignoreIfParamAbsent = ignoreIfParamAbsent;
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
