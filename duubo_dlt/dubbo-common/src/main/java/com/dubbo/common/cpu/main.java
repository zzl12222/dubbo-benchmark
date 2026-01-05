package com.dubbo.common.cpu;

import com.dubbo.common.filter.ConsumerDubboFilter;
import com.dubbo.common.filter.ProvideDubboFilter;
import java.util.ArrayList;
import java.util.List;

public class main {
    private static List<Object> bigObjectList = new ArrayList<>();
    private static final int CPU_THREAD_NUM = 2;

    public static void main(String[] args) {
        // 1. 启动监控线程
        SystemMonitorUtil.run();
        System.out.println("======= 监控启动，采集10秒JVM数据 =======");

        // 2. 启动CPU密集线程 - CPU负载50%~90% 平稳波动
        for (int i = 0; i < CPU_THREAD_NUM; i++) {
            Thread cpuThread = new Thread(() -> {
                double num = 0;
                while (true) {
                    num += Math.sin(num) * Math.cos(num) * Math.sqrt(num);
                    num %= 1000000;
                    try { Thread.sleep(3); } catch (Exception e) { break; }
                }
            });
            // ✅ 核心代码：设置为守护线程
            cpuThread.setDaemon(true);
            cpuThread.start();
        }

        // 3. 启动内存密集线程 - 内存20%~70% 稳步上涨 永不OOM
        Thread memoryThread = new Thread(() -> {
            while (true) {
                bigObjectList.add(new byte[1024 * 256]);
                bigObjectList.add(new ConsumerDubboFilter());
                bigObjectList.add(new ProvideDubboFilter());
                if (bigObjectList.size() > 1000) {
                    bigObjectList.remove(0);
                    bigObjectList.remove(0);
                    bigObjectList.remove(0);
                }
                try { Thread.sleep(10); } catch (Exception e) { break; }
            }
        });
        // ✅ 核心代码：设置为守护线程
        memoryThread.setDaemon(true);
        memoryThread.start();

        // 采集10秒数据
        try { Thread.sleep(10000); } catch (Exception e) { e.printStackTrace(); }

        // 停止监控线程
        SystemMonitorUtil.stop();

        // 打印最终数据
        System.out.println("\n======= 最终采集数据（精准无错乱+无任何报错） =======");
        System.out.println("内存使用率: " + SystemMonitorUtil.MEMORY_USAGE);
        System.out.println("CPU使用率: " + SystemMonitorUtil.CPU_USAGE);
        System.exit(0);
    }
}