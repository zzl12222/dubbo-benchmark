package com.dubbo.dlt;

import com.dubbo.common.conf.TestMode;
import com.dubbo.common.entry.TestConfig;
import com.dubbo.dlt.handler.AgentNettyHandler;
import com.dubbo.common.netty.NettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;

public class NettyServeragentService {
    private static final Logger logger = LoggerFactory.getLogger(NettyServeragentService.class);

    // 安全获取字符串环境变量
    private String safeGetEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }

    // 安全获取整数环境变量
    private Integer safeGetEnvInt(String key, Integer defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("环境变量 {} 不是有效的整数: {}, 使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    // 安全获取长整型环境变量（替换 EnvDurationUtils.getDurationInSeconds）
    private Long safeGetEnvLong(String key, Long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("环境变量 {} 不是有效的长整数: {}, 使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    // 安全获取秒数（专门处理 AGENT_DURATION_SECONDS）
    private Long safeGetDurationSeconds(String key, Long defaultValue) {
        return safeGetEnvLong(key, defaultValue);
    }

    String nettyPortTest = System.getProperty("agent.netty.port", "8888");
    private int nettyPort = Integer.parseInt(nettyPortTest);

    private Thread nettyServerThread;
    private NettyServer nettyServer;

    @PostConstruct
    public void start() {
        nettyServerThread = new Thread(() -> {
            try {
                // 使用安全方法获取所有环境变量
                String spacename = safeGetEnv("SPRING_APPLICATION_NAME", "dubbo-agent");
                String servicePort = safeGetEnv("SERVICE_PORT", "8082");

                // 替换 EnvDurationUtils.getDurationInSeconds
                Long durationSeconds = safeGetDurationSeconds("AGENT_DURATION_SECONDS", 1000L);

                Integer requestCount = safeGetEnvInt("AGENT_REQUEST_COUNT", 100);
                String loadbalacne = safeGetEnv("AGENT_LOCADBANCE", "random");
                String serialization = safeGetEnv("AGENT_SERIALIZATION", "hessian2");
                String testModeString = safeGetEnv("AGENT_TEST_MODE", "");
                TestMode testMode =  TestMode.valueOf(testModeString);
                // 记录实际使用的配置（便于调试）
                logger.info("Netty服务器配置: servicePort={}, requestCount={}, durationSeconds={}, loadbalance={}",
                        servicePort, requestCount, durationSeconds, loadbalacne);

                TestConfig testConfig = new TestConfig();
                testConfig.setLocadbance(loadbalacne);
                testConfig.setNamespace(spacename);
                testConfig.setDurationSeconds(durationSeconds);
                testConfig.setRequestCount(requestCount);
                testConfig.setSerialization(serialization);
                testConfig.setTestMode(testMode);
                int port = Integer.parseInt(servicePort);

                nettyServer = new NettyServer(port);
                AgentNettyHandler agentNettyHandler = new AgentNettyHandler(nettyServer, testConfig);
                nettyServer.setCustomHandlers(Collections.singletonList(agentNettyHandler));

                // 在新线程中启动服务器
                try {
                    nettyServer.start();
                    logger.info("Netty服务器启动成功，端口: {}", servicePort);
                } catch (Exception e) {
                    logger.error("Netty服务器启动失败", e);
                }

            } catch (Exception e) {
                logger.error("Netty服务器启动失败", e);
                throw new RuntimeException("Netty服务器启动失败", e);
            }
        },"netty-server-main");
        nettyServerThread.setDaemon(false);
        nettyServerThread.start();
    }

    @PreDestroy
    public void stop() {
        logger.info("停止Netty服务器...");

        if (nettyServer != null) {
            try {
                nettyServer.shutdown();
            } catch (Exception e) {
                logger.error("停止Netty服务器时出错", e);
            }
        }
        logger.info("Netty服务器已停止");
    }
}
