package io.micrc.lib;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 时间替换工具
 *
 * @author hyosunghan
 * @date 2022/10/21 11:29
 * @since 0.0.1
 */
public class TimeReplaceUtil {

    private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    private static DateTimeFormatter lowerDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSXXX");

    /**
     * 匹配时间路径并替换时间
     *
     * @param timePathList
     * @param path
     * @param json
     * @param timeClass
     * @return
     */
    public static String matchTimePathAndReplaceTime(List<String[]> timePathList, String path, String json, Class<?> timeClass) {
        String[] split = path.split("/");
        for (int i = 0; i < timePathList.size(); i++) {
            List<String> other = matchGetOtherPath(split, timePathList.get(i));
            json = replaceTime(json, other, timeClass);
        }
        return json;
    }

    /**
     * 替换所有符合时间路径的节点
     *
     * @param outMapping
     * @param other
     * @param timeClass
     * @return
     */
    private static String replaceTime(String outMapping, List<String> other, Class<?> timeClass) {
        if (null == other) {
            return outMapping;
        }
        StringBuilder builder = new StringBuilder();
        Iterator<String> iterator = other.iterator();
        boolean isList = false;
        boolean isMap = false;
        while (iterator.hasNext()) {
            String next = iterator.next();
            iterator.remove();
            isList = "#".equals(next);
            isMap = "*".equals(next);
            if (isList || isMap) {
                break;
            }
            builder.append("/").append(next);
        }
        Object currentObject = JsonUtil.readPath(outMapping, builder.toString());
        if (null == currentObject) {
            return outMapping;
        }
        String current = JsonUtil.writeValueAsString(currentObject);
        if (isList) {
            List<Object> list = JsonUtil.writeValueAsList(current, Object.class);
            list = list.stream().map(o -> {
                if (!other.isEmpty()) {
                    return JsonUtil.writeValueAsObject(replaceTime(JsonUtil.writeValueAsString(o), other, timeClass), Object.class);
                }
                return o;
            }).collect(Collectors.toList());
            return JsonUtil.patch(outMapping, builder.toString(), JsonUtil.writeValueAsString(list));
        } else if (isMap) {
            Map<String, Object> map = ClassCastUtils.castHashMap(JsonUtil.writeValueAsObject(current, Object.class), String.class, Object.class);
            map.forEach((k, v) -> {
                if (!other.isEmpty()) {
                    map.put(k, JsonUtil.writeValueAsObject(replaceTime(JsonUtil.writeValueAsString(v), other, timeClass), Object.class));
                }
            });
            return JsonUtil.patch(outMapping, builder.toString(), JsonUtil.writeValueAsString(map));
        } else {
            Object time = JsonUtil.readPath(outMapping, builder.toString());
            String timeResult = null;
            // 判断目标时间格式
            if (timeClass.equals(String.class)) {
                timeResult = JsonUtil.writeValueAsString(transTime2String(time));
            } else {
                timeResult = transTime2Long(time);
            }
            return JsonUtil.patch(outMapping, builder.toString(), timeResult);
        }
    }

    /**
     * 匹配路径并获取到剩余的内部路径
     *
     * @param path
     * @param timePath
     * @return
     */
    private static List<String> matchGetOtherPath(String[] path, String[] timePath) {
        if (timePath.length < path.length) {
            return null;
        }
        for (int j = 0; j < path.length; j++) {
            if (!timePath[j].equals(path[j])) {
                return null;
            }
        }
        return new ArrayList<>(Arrays.asList(timePath).subList(path.length, timePath.length));
    }

    private static String transTime2String(Object o) {
        if (null == o) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(Long.parseLong(o.toString()));
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));
        return zonedDateTime.format(dateTimeFormatter);
    }

    private static String transTime2Long(Object o) {
        if (null == o) {
            return null;
        }
        String milli = o.toString().split("\\.")[1].split("Z")[0].split("\\+")[0].split("-")[0];
        // 毫秒位数大于5位用SSSSSS匹配，否则为4位用SSSSS匹配
        DateTimeFormatter formatter = milli.length() >= 5 ? dateTimeFormatter : lowerDateTimeFormatter;
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(o.toString(), formatter);
        Instant instant = zonedDateTime.toInstant();
        return String.valueOf(instant.toEpochMilli());
    }
}
