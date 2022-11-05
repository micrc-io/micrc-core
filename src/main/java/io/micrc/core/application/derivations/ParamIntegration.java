package io.micrc.core.application.derivations;

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
     * 逻辑名称，执行指定DMN
     */
    private String logicName;

    /**
     * 路由内容路径
     */
    private String routeContentPath;

    /**
     * 路由ID路径
     */
    private String routeIdPath;

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

    public ParamIntegration(String concept, HashMap<String, String> queryParams, int order, String routeContentPath, String routeIdPath) {
        this.concept = concept;
        this.queryParams = queryParams;
        this.order = order;
        this.routeContentPath = routeContentPath;
        this.routeIdPath = routeIdPath;
        this.type = Type.EXECUTE;
    }

    public ParamIntegration(String concept, HashMap<String, String> queryParams, String logicName, int order) {
        this.concept = concept;
        this.queryParams = queryParams;
        this.order = order;
        this.logicName = logicName;
        this.type = Type.OPERATE;
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
         * 运行DMN
         */
        OPERATE,
        /**
         * 执行脚本
         */
        EXECUTE,
    }
}
