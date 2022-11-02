package io.micrc.core.persistence.snowflake;

import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class MachineNumberAliveSchedule {

    @Resource(name = "memoryDbTemplate")
    RedisTemplate<Object, Object> redisTemplate;

    @Scheduled(fixedDelay = 2 * 60 * 1000)
    @SchedulerLock(name = "MachineNumberAliveSchedule")
    public void alive() {
        if (SnowFlakeIdentity.machineNumber >= 0) {
            String key = PersistenceAutoConfiguration.MACHINE_NUMBER_KEY_PREFIX + SnowFlakeIdentity.machineNumber;
            redisTemplate.opsForValue().setIfPresent(key, "1", 5, TimeUnit.MINUTES);
        }
    }

}
