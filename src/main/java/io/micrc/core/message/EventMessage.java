package io.micrc.core.message;

import io.micrc.core.framework.json.JsonUtil;
import lombok.Data;
import org.apache.camel.Consume;
import org.h2.jdbc.JdbcClob;

import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.util.*;

/**
 * 事件消息
 *
 * @author tengwang
 * @date 2022/9/22 17:59
 * @since 0.0.1
 */
@Data
public class EventMessage {

    /**
     * 消息ID
     */
    private String messageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

    /**
     * 消息创建时间
     */
    private Long createTime = System.currentTimeMillis();

    private String content;

    private Long sequence;

    private String region;

    public EventMessage store(String command, String event) {
        EventMessage eventMessage = new EventMessage();
        eventMessage.setContent(command);
        eventMessage.setRegion(event);
        return eventMessage;
    }

    /**
     * 下划线转驼峰
     *
     * @param name
     * @return
     */
    private static String toCamelName(String name) {
        String spliter = "_";
        StringBuffer output = new StringBuffer();
        String[] words = name.toLowerCase().split(spliter);
        for (int i = 0; i < words.length; i++) {
            if (i != 0) {
                output.append(fistLetterToUpper(words[i]));
            } else {
                output.append(words[i]);
            }
        }
        return output.toString();
    }

    private static String fistLetterToUpper(String input) {
        if (input == null)
            return "";
        if (input.length() <= 0)
            return "";
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    @Consume("eventstore://convert-clob")
    public List<EventMessage> convertClob(List<LinkedHashMap<String, Object>> resultSet) {
        List<EventMessage> eventMessages = new ArrayList<>();
        resultSet.stream().forEach(map -> {
                    Map<String, Object> messageMap = new HashMap<>();
                    map.keySet().stream().forEach(key -> {
                        Object value = map.get(key);
                        if (value instanceof JdbcClob) {
                            StringBuffer stringBuffer = new StringBuffer();
                            try {
                                Reader rd = ((JdbcClob) value).getCharacterStream();
                                char[] str = new char[12];
                                while (rd.read(str) != -1) {
                                    stringBuffer.append(new String(str));
                                }
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            messageMap.put(toCamelName(key), stringBuffer.toString());
                        } else {
                            messageMap.put(toCamelName(key), map.get(key));
                        }
                    });
                    eventMessages.add(JsonUtil.writeObjectAsObject(messageMap, EventMessage.class));
                }
        );
        return eventMessages;
    }

    @Data
    public static class ErrorMessage {

        /**
         * 失败消息ID
         */
        private String errorMessageId = System.currentTimeMillis() + java.util.UUID.randomUUID().toString().replaceAll("-", "");

        /**
         * 序列号
         */
        private Long sequence;

        /**
         * 发送通道
         */
        private String channel;

        /**
         * 发送交换区
         */
        private String exchange;

        /**
         * 消息类型
         */
        private String region;

        /**
         * 失败原因 send error reason - the SEND is in send step error, the DEAD_MESSAGE is on consumer can not consumer error
         */
        private String reason;

        /**
         * 最后失败时间
         */
        private Long lastErrorTime = System.currentTimeMillis();

        /**
         * 失败次数
         */
        private Integer errorFrequency = 1;

        public ErrorMessage haveError(String region, Long sequence, String exchange, String channel, String reason) {
            ErrorMessage errorMessage = new ErrorMessage();
            errorMessage.setRegion(region);
            errorMessage.setSequence(sequence);
            errorMessage.setExchange(exchange);
            errorMessage.setChannel(channel);
            errorMessage.setReason(reason);
            return errorMessage;
        }
    }
}
