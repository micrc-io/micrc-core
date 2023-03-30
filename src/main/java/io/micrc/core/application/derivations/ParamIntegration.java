package io.micrc.core.application.derivations;

import io.micrc.core.annotations.application.derivations.TechnologyType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
     * 查询参数路径
     *
     * @return
     */
    private Map<String, String> paramMappingMap;

    /**
     * 参数映射
     */
    private List<String> paramMappings;

    /**
     * 内容位置
     */
    private String contentPath;

    /**
     * 文件位置
     */
    private String filePath;

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

    private TechnologyType technologyType;

    public ParamIntegration(String concept, String contentPath, String filePath, Map<String, String> paramMappingMap, int order, Type type, TechnologyType technologyType) {
        this.concept = concept;
        this.paramMappingMap = paramMappingMap;
        this.order = order;
        this.contentPath = contentPath;
        this.filePath = filePath;
        this.type = type;
        this.technologyType = technologyType;
    }

    public ParamIntegration(String concept, String entityPath, String queryMethod, List<String> paramMappings, int order) {
        this.concept = concept;
        this.entityPath = entityPath;
        this.queryMethod = queryMethod;
        this.paramMappings = paramMappings;
        this.order = order;
        this.type = Type.QUERY;
    }

    /**
     * 类型
     */
    public enum Type {
        /**
         * 查库
         */
        QUERY,

        /**
         * 通用技术
         */
        GENERAL_TECHNOLOGY,

        /**
         * 专用技术
         */
        SPECIAL_TECHNOLOGY,
        ;
    }
}
