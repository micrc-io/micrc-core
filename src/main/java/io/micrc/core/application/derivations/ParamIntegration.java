package io.micrc.core.application.derivations;

import io.micrc.core.annotations.application.derivations.TechnologyType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
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
     * 聚合
     *
     * @return
     */
    private String aggregation;

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
    private Map<String, String> queryParams;

    /**
     * 排序参数方式
     */
    private Map<String, String> sortParams;

    /**
     * 分页编码路径
     */
    private String pageNumberPath;

    /**
     * 分页大小路径
     */
    private String pageSizePath;

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

    public ParamIntegration(String concept, HashMap<String, String> queryParams, String contentPath, String filePath, int order, Type type, TechnologyType technologyType) {
        this.concept = concept;
        this.queryParams = queryParams;
        this.order = order;
        this.contentPath = contentPath;
        this.filePath = filePath;
        this.type = type;
        this.technologyType = technologyType;
    }

    public ParamIntegration(String concept, String aggregation, String queryMethod,
                            Map<String, String> queryParams, Map<String, String> sortParams,
                            String pageSizePath, String pageNumberPath, int order) {
        this.concept = concept;
        this.aggregation = aggregation;
        this.queryMethod = queryMethod;
        this.queryParams = queryParams;
        this.sortParams = sortParams;
        this.pageSizePath = pageSizePath;
        this.pageNumberPath = pageNumberPath;
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
