package io.micrc.core.framework.identify;

/**
 * 主键生成策略
 *
 * @Author: tengwang
 * @Date: 2018/1/12 10:47
 * @Version v1.0.0
 */
public class IdGenerator {

    /**
     * 获取标准ID
     */
    public static String getIdentify() {
        return System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");
    }


    /**
     * 获取UUID32位字符
     */
    public static String getUUID() {
        return java.util.UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 获取GUID36位字符
     */
    public static String getGUID() {
        return java.util.UUID.randomUUID().toString();
    }
}
