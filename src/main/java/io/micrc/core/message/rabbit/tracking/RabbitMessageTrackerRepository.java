package io.micrc.core.message.rabbit.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 消息跟踪器仓库
 *
 * @author tengwang
 * @version 1.0.0
 * @date 2022/1/12 9:11 PM
 */
@Repository
public interface RabbitMessageTrackerRepository extends JpaRepository<RabbitMessageTracker, String> {

    RabbitMessageTracker findFirstByChannel(String channel);
}
