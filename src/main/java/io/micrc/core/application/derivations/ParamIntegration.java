package io.micrc.core.application.derivations;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;

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
    private HashMap<String, String> params;

    /**
     * 逻辑名称，执行指定DMN
     */
    private String logicName;

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

    public ParamIntegration(String concept, HashMap<String, String> params, String logicName, int order) {
        this.concept = concept;
        this.params = params;
        this.order = order;
        this.logicName = logicName;
        this.type = Type.OPERATE;
    }

    public ParamIntegration(String concept, String aggregation, String queryMethod, HashMap<String, String> queryParams, int order) {
        this.concept = concept;
        this.aggregation = aggregation;
        this.queryMethod = queryMethod;
        this.params = queryParams;
        this.order = order;
        this.type = Type.QUERY;
    }

    /**
     * 类型
     */
    public enum Type {
        QUERY,
        OPERATE,
    }
}
