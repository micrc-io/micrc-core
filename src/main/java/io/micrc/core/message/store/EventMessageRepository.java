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
                    "and ms.message_id > :currentSequence " +
                    "order by ms.sequence asc " +
                    "limit :count ")
    List<EventMessage> findEventMessageByRegionAndCurrentSequenceLimitByCount(
            @Param(value = "region") String region,
            @Param(value = "currentSequence") Long currentSequence,
            @Param(value = "count") Integer count);
}