package io.micrc.core.message.tracking;

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
public interface ErrorMessageRepository extends JpaRepository<ErrorMessage, String> {

    @Query(nativeQuery = true,
           value = "select mem.* from message_error_message mem " +
                   "where " +
                   "mem.exchange= :exchange " +
                   "and " +
                   "mem.channel= :channel " +
                   "and mem.state='NOT_SEND' order by mem.sequence ASC " +
                   "limit :count ")
    List<ErrorMessage> findErrorMessageByExchangeAndChannelLimitByCount(
            @Param(value = "exchange") String exchange,
            @Param(value = "channel") String channel,
            @Param(value = "count") Integer count
    );

    void deleteByExchangeAndChannelAndSequenceAndRegion(
            @Param(value = "exchange") String exchange,
            @Param(value = "channel") String channel,
            @Param(value = "sequence") Long sequence,
            @Param(value = "region") String region
    );

    ErrorMessage findFirstByExchangeAndChannelAndSequenceAndRegion(
            @Param(value = "exchange") String exchange,
            @Param(value = "channel") String channel,
            @Param(value = "sequence") Long sequence,
            @Param(value = "region") String region
    );
}
