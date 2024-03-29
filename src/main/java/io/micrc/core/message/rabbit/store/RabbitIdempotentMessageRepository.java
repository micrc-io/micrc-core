//package io.micrc.core.message.rabbit.store;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
///**
// * 消息幂等仓库
// *
// * @author tengwang
// * @version 1.0.0
// * @date 2022/1/12 9:11 PM
// */
//@Repository
//public interface RabbitIdempotentMessageRepository extends JpaRepository<RabbitIdempotentMessage, String> {
//
//    RabbitIdempotentMessage findFirstByExchangeAndChannelAndSequenceAndRegion(
//            @Param(value = "exchange") String exchange,
//            @Param(value = "channel") String channel,
//            @Param(value = "sequence") Long sequence,
//            @Param(value = "region") String region
//    );
//}
