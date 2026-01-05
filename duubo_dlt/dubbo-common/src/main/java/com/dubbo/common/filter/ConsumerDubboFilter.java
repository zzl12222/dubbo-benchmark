package com.dubbo.common.filter;

import com.dubbo.common.aop.DubboInvokeStat;
import com.dubbo.common.entry.ConsumerTestResult;
import com.dubbo.common.entry.TestConfig;
import com.dubbo.common.entry.TestStatContext;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo 消费端调用统计过滤器
 * 作用：对带有@DubboInvokeStat注解的方法，进行调用次数、耗时、成功率、并发数、Provider归属统计
 */
@Activate(group = {CommonConstants.CONSUMER}, order = 100)
public class ConsumerDubboFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ConsumerDubboFilter.class);
    /**
     * 核心：consumerId -> 测试上下文 映射
     */
    private static final Map<String, TestStatContext> testContexts = new ConcurrentHashMap<>();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 1. 校验是否有统计注解，无则直接执行调用，不走统计逻辑
        boolean hasStatAnnotation = hasDubboInvokeStatAnnotation(invoker, invocation);
        if (!hasStatAnnotation) {
            return invoker.invoke(invocation);
        }
        // 2. 有注解则执行带统计的调用逻辑
        return executeWithTestStat(invoker, invocation);
    }

    /**
     * 开始一个新的测试
     * @param consumerId 测试ID/消费者ID 作为KEY
     */
    public static TestStatContext startTest(String consumerId) {
        TestStatContext context = new TestStatContext(consumerId);
        testContexts.put(consumerId, context);
        log.info("测试任务启动 - consumerId:{}", consumerId);
        return context;
    }

    /**
     * 结束测试并获取结果
     * @param consumerId 测试ID/消费者ID
     */
    public static ConsumerTestResult endTest(TestConfig config, String consumerId) {
        TestStatContext context = testContexts.remove(consumerId);
        if (context != null) {
            context.endTest();
            ConsumerTestResult testResult = context.toTestResult();
            testResult.setFailedRequests(config.getRequestCount() - testResult.getSuccessfulRequests());
            testResult.setTotalRequests(config.getRequestCount());
            return testResult;
        }
        log.warn("结束测试失败 - 未找到consumerId对应的测试上下文:{}", consumerId);
        return null;
    }

    /**
     * 获取测试上下文
     */
    public static TestStatContext getTestContext(String consumerId) {
        return testContexts.get(consumerId);
    }

    /**
     * 检查方法是否有@DubboInvokeStat注解
     */
    private boolean hasDubboInvokeStatAnnotation(Invoker<?> invoker, Invocation invocation) {
        try {
            Class<?> serviceInterface = invoker.getInterface();
            String methodName = invocation.getMethodName();
            Class<?>[] paramTypes = invocation.getParameterTypes();

            Method method = serviceInterface.getMethod(methodName, paramTypes);
            return method.isAnnotationPresent(DubboInvokeStat.class);
        } catch (NoSuchMethodException e) {
            // 容错：匹配方法名+参数个数，兼容泛型等场景
            for (Method m : invoker.getInterface().getMethods()) {
                if (m.getName().equals(invocation.getMethodName()) &&
                        m.getParameterCount() == invocation.getArguments().length) {
                    return m.isAnnotationPresent(DubboInvokeStat.class);
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("检查@DubboInvokeStat注解失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 核心：执行带有完整测试统计的调用【修复所有BUG后的核心方法】
     */
    private Result executeWithTestStat(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String consumerId = invocation.getAttachment("consumerId"); // 从Dubbo隐式传参中获取consumerId
        TestStatContext testContext = testContexts.get(consumerId);
        if (testContext == null) {
            log.debug("无测试上下文，执行普通调用 - consumerId:{}, method:{}", consumerId, invocation.getMethodName());
            return invoker.invoke(invocation);
        }

        // 并发数+1
        testContext.increaseConcurrent();
        long startTime = System.currentTimeMillis();
        String providerName = "unknown-provider";
        Result result = null;
        try {
            result = invoker.invoke(invocation);
            RpcContext rpcContext = RpcContext.getContext();
            URL url = rpcContext.getUrl();
            if (url != null) {
                providerName = url.getRemoteApplication();
                if (providerName == null || providerName.isEmpty()) {
                    providerName = url.getAddress();
                }
            }
            long responseTime = System.currentTimeMillis() - startTime;
            testContext.recordSuccess(responseTime);
            testContext.recordProvider(providerName + "#" + invocation.getMethodName());
            log.debug("调用成功 - consumerId:{}, provider:{}, method:{}, 耗时:{}ms, 当前并发数:{}",
                    consumerId, providerName, invocation.getMethodName(), responseTime, testContext.getCurrentConcurrent());

            return result;
        } catch (Exception e) {
            testContext.recordFailure();
            log.error("调用失败 - consumerId:{}, provider:{}, method:{}, 错误信息:{}",
                    consumerId, providerName, invocation.getMethodName(), e.getMessage(), e);

            throw new RpcException("Dubbo调用失败，已记录统计信息", e);
        } finally {
            testContext.decreaseConcurrent();
            log.debug("调用结束 - consumerId:{}, method:{}, 释放并发后当前并发数:{}",
                    consumerId, invocation.getMethodName(), testContext.getCurrentConcurrent());
        }
    }
}