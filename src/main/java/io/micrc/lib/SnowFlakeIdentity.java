package io.micrc.lib;

/**
 * 雪花算法生成实体主键ID
 *
 * @author weiguan
 * @date 2022-9-26 20:39
 * @since 0.0.1
 */
public final class SnowFlakeIdentity {
    private SnowFlakeIdentity() {}

    // TODO tengwang 完成这个算法, 不要有参数传入，节点ID等参数，算法内自行获取(比如从系统属性、环境变量中获取)
    public static long generate() {
        return Long.MIN_VALUE;
    }
}
