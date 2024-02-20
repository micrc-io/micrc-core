package io.micrc.core.message.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 事件存储仓库
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/12 9:11 PM
 */
@Repository
public interface EventMessageRepository extends JpaRepository<EventMessage, Long> {
    @Query(nativeQuery = true,
            value = "select ms.* from message_message_store ms " +
                    "where " +
                    "ms.region = :region " +
                    "and ms.status ='WAITING' " +
                    "and (json_extract(ms.content,'$.event.appointmentTime') is null or json_extract(ms.content,'$.event.appointmentTime') < UNIX_TIMESTAMP() * 1000)" +
                    "order by ms.message_id asc " +
                    "limit :count ")
    List<EventMessage> findEventMessageByRegionLimitByCount(
            @Param(value = "region") String region, @Param(value = "count") Integer count);

    @Query(nativeQuery = true,
            value = "select ms.* from message_message_store ms " +
                    "where " +
                    "ms.original_topic is not null " +
                    "and ms.status ='WAITING' " +
                    "order by ms.message_id asc " +
                    "limit 1000 ")
    List<EventMessage> findEventMessageByOriginalExists();

    /**
     * 清理入口，已发送的事件1000条
     *
     * @param region    region
     * @param count     count
     * @return          message ids
     */
    @Query(nativeQuery = true,
            value = "select ms.message_id from message_message_store ms " +
                    "where " +
                    "ms.region = :region " +
                    "and ms.status ='SENT' " +
                    "order by ms.message_id asc " +
                    "limit :count")
    List<Long> findSentIdByRegionLimitCount(@Param(value = "region") String region, @Param("count") Integer count);

    @Query(nativeQuery = true,
            value = "select ms.message_id from message_message_store ms " +
                    "where " +
                    "ms.original_topic is not null " +
                    "and ms.status ='SENT' " +
                    "order by ms.message_id asc " +
                    "limit 1000 ")
    List<Long> findSentIdByOriginalExists();

    /**
     * 清理检查
     *
     * @param messageIds    messageIds
     * @return              messageIds
     */
    @Query(nativeQuery = true,
            value = "select ms.message_id from message_message_store ms " +
                    "where ms.message_id in :messageIds")
    List<Long> findUnRemoveIdsByMessageIds(@Param(value = "messageIds") List<Long> messageIds);
}