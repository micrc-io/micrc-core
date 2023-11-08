package io.micrc.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 类转换工具
 *
 * @author tengwang
 * @date 2022/9/21 19:42
 * @since 0.0.1
 */
public final class ClassCastUtils {

    /**
     * Map对象转换具有泛型的HashMap
     *
     * @param obj        params
     * @param keyClass   the map key class
     * @param valueClass the map value class
     * @param <K>        the map key class
     * @param <V>        the map value class
     * @return params convert result
     */
    public static <K, V> Map<K, V> castHashMap(Object obj, Class<K> keyClass, Class<V> valueClass) {
        Map<K, V> retVal = new LinkedHashMap<>();
        if (obj instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                retVal.put(keyClass.cast(entry.getKey()), valueClass.cast(entry.getValue()));
            }
            return retVal;
        }
        return null;
    }

    /**
     * List对象转换为具有泛型的ArrayList
     *
     * @param obj        params
     * @param valueClass the value class
     * @param <T>        the value class
     * @return           value list
     */
    public static <T> List<T> castArrayList(Object obj, Class<T> valueClass) {
        List<T> retVal = new ArrayList<>();
        if (obj instanceof List<?>) {
            for (Object value : ((List<?>) obj).toArray()) {
                retVal.add(valueClass.cast(value));
            }
            return retVal;
        }
        return null;
    }
}
