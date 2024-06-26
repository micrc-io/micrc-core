package io.micrc.core.persistence.springboot;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.*;

import java.util.*;

/**
 * persistence env processor. mysql, jpa and liquibase config
 *
 * @author weiguan
 * @date 2022-09-01 15:36
 * @since 0.0.1
 */
public class PersistenceEnvironmentProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public PersistenceEnvironmentProcessor(Log log) {
        this.log = log;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<String> profileStr = Optional.ofNullable(environment.getProperty("application.profiles"));
        List<String> profiles = Arrays.asList(profileStr.orElse("").split(","));
        Properties properties = new Properties();
        envForEmbeddedMysql(profiles, properties, obtainProvider("DATABASE", environment));
        envForLiquibase(profiles, properties, environment);
        envForJPA(profiles, properties);
        bootstrapEmbeddedMemoryDb(profiles, properties, obtainProvider("MEMDB", environment));

        PropertiesPropertySource source = new PropertiesPropertySource("micrc-persistence", properties);
        environment.getPropertySources().addLast(source);
    }

    private void envForEmbeddedMysql(Collection<String> profiles, Properties properties, String provider) {
        if (!profiles.contains("default")) {
            properties.setProperty("embedded.mysql.enabled", "false");
        }
        if (profiles.contains("default")) {
            log.info("Embedded mysql server configuration for profile: 'default'");
            // embedded mysql
            properties.setProperty("embedded.mysql.enabled", "true");
            properties.setProperty("embedded.mysql.reuseContainer", "true");
            properties.setProperty("embedded.mysql.dockerImage", "mysql:8.0.30");
            properties.setProperty("embedded.mysql.waitTimeoutInSeconds", "60");
            properties.setProperty("embedded.mysql.encoding", "utf8mb4");
            properties.setProperty("embedded.mysql.collation", "utf8mb4_unicode_ci");

            properties.setProperty("micrc.embedded.mysql.host", "${embedded.mysql.host}");
            properties.setProperty("micrc.embedded.mysql.port", "${embedded.mysql.port}");
        }
        //
        if (profiles.contains("default")) {
            // default mysql connection
            properties.setProperty("spring.datasource.url",
                    "jdbc:mysql://${embedded.mysql.host}:${embedded.mysql.port}/${embedded.mysql.schema}" +
                            "?useunicode=true&characterencoding=utf8&servertimezone=utc");
            properties.setProperty("spring.datasource.username", "${embedded.mysql.user}");
            properties.setProperty("spring.datasource.password", "${embedded.mysql.password}");
        } else {
            // k8s集群中按约定名称读取的secret中的host, port, user, pass属性
            properties.setProperty("spring.datasource.url",
                "jdbc:mysql://${" + provider + "_database_host}:${" + provider + "_database_port}/${" + provider + "_database_name}");
            properties.setProperty("spring.datasource.username", "${" + provider + "_database_user}");
            properties.setProperty("spring.datasource.password", "${" + provider + "_database_password}");
        }
        properties.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        // 数据源和连接池通用配置 https://github.com/brettwooldridge/HikariCP
        properties.setProperty("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource");
        // 最小空闲连接数量
        properties.setProperty("spring.datasource.hikari.minimum-idle", "10");
        // 空闲连接存活最大时间，默认600000（10分钟）
        properties.setProperty("spring.datasource.hikari.idle-timeout", "600000");
        // 连接池最大连接数，默认是10
        properties.setProperty("spring.datasource.hikari.maximum-pool-size", "1000");
        // 此属性控制从池返回的连接的默认自动提交行为,默认值：true
        properties.setProperty("spring.datasource.hikari.auto-commit", "true");
        // 连接池名称
        properties.setProperty("spring.datasource.hikari.pool-name", "OfficialWebsiteHikariCP");
        // 心跳检测时间
        properties.setProperty("spring.datasource.hikari.keepalive-time", "60000");
        // 此属性控制池中连接的最长生命周期，值0表示无限生命周期，默认7140000即119分钟
        properties.setProperty("spring.datasource.hikari.max-lifetime", "7140000");
        // 数据库连接超时时间,默认30秒，即30000
        properties.setProperty("spring.datasource.hikari.connection-timeout", "300000");
        properties.setProperty("spring.datasource.hikari.connection-test-query", "SELECT 1");
    }

    private void envForLiquibase(Collection<String> profiles, Properties properties, Environment env) {
        properties.setProperty("spring.liquibase.enabled", "false"); // 默认先关闭
        // 两种情况下，liquibase在app内打开
        // 1. 系统作为liquibase数据库迁移app时，独立打包成image在init container中初始化数据库
        // 2. 当系统profile为default(本地开发)时，系统自动通过snapshot和当前版本changelog每次启动时初始化数据库
        // note: local(本地集群)环境，也是使用dbinit独立image在init container中初始化数据库
        if (profiles.contains("dbinit") || profiles.contains("default")) {
            log.info("Active Profile: 'dbinit' Or 'default'. Enable liquibase For Database Init.");
            properties.setProperty("spring.liquibase.enabled", "true");
        }
        properties.setProperty("spring.liquibase.changeLog", "db/master.yaml");
        log.debug("Persistence Properties: \n" + properties);
    }

    private void envForJPA(Collection<String> profiles, Properties properties) {
        properties.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.MySQL8Dialect");
        properties.setProperty("spring.jpa.open-in-view", "false");
        properties.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        if (profiles.contains("default")) {
            properties.setProperty("logging.level.org.springframework.orm.jpa", "ERROR");
            properties.setProperty("logging.level.org.springframework.transaction", "ERROR");
            properties.setProperty("spring.jpa.properties.hibernate.show_sql", "false");
            properties.setProperty("spring.jpa.properties.hibernate.format_sql", "true");
        }
    }

    private void bootstrapEmbeddedMemoryDb(List<String> profiles, Properties properties, String provider) {
        if (profiles.contains("default")) {
            // default redis connection
            properties.setProperty("micrc.spring.memory-db.host", "${embedded.redistack.host}");
            properties.setProperty("micrc.spring.memory-db.port", "${embedded.redistack.port}");
            properties.setProperty("micrc.spring.memory-db.password", "${embedded.redistack.password}");
            properties.setProperty("micrc.spring.memory-db.username", "${embedded.redistack.username}");
        } else {
            // k8s集群中读取的configmap中的host，port和passwd
            properties.setProperty("micrc.spring.memory-db.host", "${" + provider + "_memdb_host}");
            properties.setProperty("micrc.spring.memory-db.port", "${" + provider + "_memdb_port}");
            properties.setProperty("micrc.spring.memory-db.password", "${" + provider + "_memdb_password}");
            properties.setProperty("micrc.spring.memory-db.username", "${" + provider + "_memdb_username}");
        }
        // 任何环境使用统一的连接配置
        properties.setProperty("micrc.spring.memory-db.database", "15");
        properties.setProperty("micrc.spring.memory-db.timeout", "3000");
        properties.setProperty("micrc.spring.memory-db.lettuce.pool.max-active", "1000");
        properties.setProperty("micrc.spring.memory-db.lettuce.pool.min-idle", "5");
        properties.setProperty("micrc.spring.memory-db.lettuce.pool.max-idle", "10");
        properties.setProperty("micrc.spring.memory-db.lettuce.pool.max-wait", "-1");
        properties.setProperty("micrc.spring.memory-db.pool.time-between-eviction-runs-millis", "2000");
    }

    // 仅允许连接一个数据库或内存库，取providers中第一个
    private String obtainProvider(String middleware, ConfigurableEnvironment env) {
        String providersString = (String) env.getSystemEnvironment().getOrDefault(middleware + "_PROVIDERS", "");
        String[] providers = providersString.split(",");
        if (providers.length != 1) { // 配置错误，直接报错停止启动
            throw new RuntimeException();
        }
        return providers[0];
    }
}
