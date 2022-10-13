package io.micrc.core.persistence.snowflake;

/**
 * 雪花算法ID生成器
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-10-02 03:22
 */
public class SnowFlakeIdentity {

    /**
     * 时间戳基准值2022-01-01 00:00:00（时间戳截止值2039-01-01 00:00:00）
     */
    private static final long TIMESTAMP_DATUM = 1640995200000L;

    /**
     * 序列号位数
     */
    private static final int SEQUENCE_BITS = 9;

    /**
     * 机器码位数
     */
    private static final int MACHINE_NUMBER_BITS = 15;

    /**
     * 最大序列号:511(该算法可计算固定位数的二进制所能表达的最大十进制数)
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /**
     * 最大机器码:32767(该算法可计算固定位数的二进制所能表达的最大十进制数)
     */
    public static final long MAX_MACHINE_NUMBER = ~(-1L << MACHINE_NUMBER_BITS);

    private static SnowFlakeIdentity instance;

    /**
     * 机器码（由业务服务初始化）
     */
    public static int machineNumber = -1;

    /**
     * 历史时间戳，默认从当前开始，后续自增
     */
    private long pastTimeStamp = System.currentTimeMillis();

    /**
     * 当前序列号，默认从0开始，后续自增
     */
    private int currentSequence = 0;


    private SnowFlakeIdentity() {

    }

    public static SnowFlakeIdentity getInstance() {
        if (instance == null) {
            synchronized(SnowFlakeIdentity.class) { // 注意这里是类级别的锁
                if (instance == null) {       // 这里的检测避免多线程并发时多次创建对象
                    if (machineNumber < 0) {
                        // 机器码未初始化，不能生成正确的机器码
                        throw new IllegalStateException("Please wait machine number allot.");
                    }
                    instance = new SnowFlakeIdentity();
                }
            }
        }
        return instance;
    }

    /**
     * 下一个ID
     *
     * @return
     */
    public synchronized long nextId() {

        long past = pastTimeStamp;
        checkTime(past);

        long sequence = currentSequence++;
        if (sequence > MAX_SEQUENCE) {
            past = ++pastTimeStamp;
            checkTime(past);
            currentSequence = 0;
            sequence = currentSequence++;
        }

        return (past - TIMESTAMP_DATUM) << MACHINE_NUMBER_BITS << SEQUENCE_BITS
                | sequence << MACHINE_NUMBER_BITS
                | machineNumber;
    }

    /**
     * 检查时间
     *
     * @param past
     */
    private void checkTime(long past) {
        while (past > System.currentTimeMillis()) {
            // wait time go
        }
    }
}
