package com.dubbo.common.produce;

import com.dubbo.common.conf.ClientType;
import com.dubbo.common.conf.ControlCommand;
import com.dubbo.common.conf.MessageType;
import com.dubbo.common.entry.Message;
import com.dubbo.common.entry.QoPData;
import com.dubbo.common.netty.NettyClient;
import com.dubbo.common.netty.lister.MessageListener;
import com.dubbo.common.netty.protocol.*;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NettyProduce implements MessageListener {
    private Logger logger = LoggerFactory.getLogger(NettyProduce.class);

    private String agentHost = getSafeEnvString("AGENT_HOST", "localhost");
    private int agentPort = getSafeEnvInt("AGENT_PORT", 8082);
    private String providerId = getSafeEnvString("SPRING_APPLICATION_NAME", "provide_1");
    private NettyClient nettyClient;
    private ScheduledExecutorService scheduler;
    private volatile boolean isRegistered = false;
    private volatile boolean isRunning = true;
    private String getSafeEnvString(String envKey, String defaultValue) {
        try {
            String value = System.getenv(envKey);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            return value.trim();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getSafeEnvInt(String envKey, int defaultValue) {
        try {
            String value = System.getenv(envKey);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }

            String trimmedValue = value.trim();
            return Integer.parseInt(trimmedValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    @PostConstruct
    public void init() {
        try {
            nettyClient = new NettyClient(agentHost, agentPort, providerId);
            ProduceHandler produceHandler = new ProduceHandler(providerId);
            nettyClient.setChannelHandler(Collections.singletonList(produceHandler));
            connectToAgent();
            startScheduledTasks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectToAgent() {
        try {
            nettyClient.connect();
            registerToAgent();
        } catch (Exception e) {
            scheduleReconnect();
        }
    }

    private void registerToAgent() {
        RegisterMessage registerMsg = new RegisterMessage();
        registerMsg.setTimestamp(System.currentTimeMillis());
        Message message = new Message();
        message.setData(JSONObject.toJSONString(registerMsg));
        message.setType(MessageType.REGISTER);
        message.setTimestamp(System.currentTimeMillis());
        message.setClientId(providerId);
        message.setClientType(ClientType.PROVIDER);
        nettyClient.sendMessage(message);
        isRegistered = true;
    }

    private void startScheduledTasks() {
        scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(() -> {
            if (nettyClient.isConnected() && isRegistered) {
                HeartbeatMessage heartbeat = new HeartbeatMessage();
                heartbeat.setClientId("provider-" + providerId);
                double currentQps = 11.0;
                heartbeat.setLoad((int) currentQps);
                Message message = new Message();
                message.setData(JSONObject.toJSONString(heartbeat));
                message.setType(MessageType.HEARTBEAT);
                message.setTimestamp(System.currentTimeMillis());
                message.setClientId(providerId);
                message.setClientType(ClientType.PROVIDER);
                nettyClient.sendMessage(message);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }


    @Override
    public void onMessage(Message message) {
        switch (message.getType()) {
            case ACK:
                handleAckMessage((AckMessage) message);
                break;
            case CONTROL:
                handleControlMessage((ControlMessage) message);
                break;
            case SHUTDOWN:
                handleShutdownMessage((ShutdownMessage) message);
                break;
            default:
                logger.info("Produce not find commend: " + message.getType());
        }
    }
    
    @Override
    public void onControlMessage(ControlMessage message) {
        handleControlMessage(message);
    }
    
    @Override
    public void onShutdown(ShutdownMessage message) {
        handleShutdownMessage(message);
    }

    private void handleAckMessage(AckMessage ack) {
        if (ack.isSuccess()) {
            
            if ("注册成功".equals(ack.getMessage())) {
                isRegistered = true;
            }
        } else {
            logger.error("Produce  accept bad commend: " + ack.getMessage());
        }
    }

    private void handleControlMessage(ControlMessage controlMsg) {
        if (controlMsg.getCommand() == ControlCommand.REQUEST_QOP) {
            handleQoPRequest();
        } else {
            logger.info("Produce not do this MessageType " + controlMsg.getCommand());
        }
    }

    private void handleQoPRequest() {
        try {
            QoPData qoPData = new QoPData();
            QoPMessage qoPMessage = new QoPMessage();
            qoPMessage.setQoPData(qoPData);
            qoPMessage.setTimestamp(System.currentTimeMillis());
            
            nettyClient.sendMessage(qoPMessage);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleShutdownMessage(ShutdownMessage shutdownMsg) {
        AckMessage ack = new AckMessage();
        ack.setRequestId(shutdownMsg.getMessageId());
        ack.setSuccess(true);
        ack.setTimestamp(System.currentTimeMillis());
        nettyClient.sendMessage(ack);
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }).start();
    }

    private void scheduleReconnect() {
        ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        reconnectScheduler.schedule(() -> {
            if (isRunning) {
                connectToAgent();
            }
            reconnectScheduler.shutdown();
        }, 5, TimeUnit.SECONDS);
    }
    
    @PreDestroy
    public void shutdown() {
        isRunning = false;

        if (nettyClient != null && nettyClient.isConnected()) {
            ShutdownMessage shutdownMsg = new ShutdownMessage();
            shutdownMsg.setReason("Produce shutdown");
            shutdownMsg.setTimestamp(System.currentTimeMillis());
            nettyClient.sendMessage(shutdownMsg);
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        if (nettyClient != null) {
            nettyClient.disconnect();
        }
    }
}