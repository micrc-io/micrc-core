package io.micrc.core.persistence.snowflake;

import io.micrc.core.persistence.springboot.PersistenceAutoRunner;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MachineNumberAliveSchedule {

    @Resource(name = "memoryDbTemplate")
    RedisTemplate<Object, Object> redisTemplate;

    @Scheduled(fixedDelay = 2 * 60 * 1000)
    @SchedulerLock(name = "MachineNumberAliveSchedule")
    public void alive() {
        if (SnowFlakeIdentity.machineNumber < 0) {
            return;
        }
        String key = PersistenceAutoRunner.MACHINE_NUMBER_KEY_PREFIX + SnowFlakeIdentity.machineNumber;
        Boolean done = redisTemplate.opsForValue().setIfPresent(key, "1", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(done)) {
            log.error("Machine number alive failure.");
        }
    }

}
