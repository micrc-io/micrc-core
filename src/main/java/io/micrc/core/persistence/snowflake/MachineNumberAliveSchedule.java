package io.micrc.core.persistence.snowflake;

import io.micrc.core.persistence.springboot.PersistenceAutoConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

public class MachineNumberAliveSchedule {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 2 * 60 * 1000)
    @SchedulerLock(name = "MachineNumberAliveSchedule")
    public void alive() {
        String key = PersistenceAutoConfiguration.MACHINE_NUMBER_KEY_SUFFIX + SnowFlakeIdentity.machineNumber;
        stringRedisTemplate.opsForValue().setIfPresent(key, "1", 5, TimeUnit.MINUTES);
    }

}
