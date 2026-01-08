package com.dubbo.produce;

import com.dubbo.common.cpu.SystemMonitorUtil;
import com.dubbo.common.produce.NettyProduce;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = {"com.dubbo.provide","com.dubbo.common"})
@EnableDubbo
public class ProduceApplication {

    private static final Logger log = LoggerFactory.getLogger(com.dubbo.produce.ProduceApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(com.dubbo.produce.ProduceApplication.class, args);
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(NettyProduce.class);
        beanFactory.registerBeanDefinition("nettyProvideService", beanDefinitionBuilder.getBeanDefinition());
        NettyProduce nettyProduce = context.getBean(NettyProduce.class);
        SystemMonitorUtil.run();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            context.close();
        }));
    }
}