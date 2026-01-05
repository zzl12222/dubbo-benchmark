package com.dubbo.common.scan;

import com.dubbo.common.aop.DubboInvokeStat;
import com.dubbo.common.aop.EnableDubboTest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

@Slf4j
@Component
public class SimpleDubboScanner implements CommandLineRunner {

    private static final String RESOURCE_PATTERN = "**/*.class";

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Class<?>, List<Method>> interfaceMethods = new HashMap<>();
    private EnableDubboTest enableDubboTest;

    @PostConstruct
    public void init() throws ClassNotFoundException {
        boolean hasAnnotation = isAnnotationPresentOnMainClass(EnableDubboTest.class);
        log.info("是否存在 @EnableDubboTest 注解: {}", hasAnnotation);

        if (hasAnnotation) {
            String mainClassName = getMainClassName();

            Class<?> mainClass = Class.forName(mainClassName);
            this.enableDubboTest = mainClass.getAnnotation(EnableDubboTest.class);

            log.info("@EnableDubboTest 注解检测成功，将在 Spring 启动完成后执行扫描");
        } else {
            this.enableDubboTest = null;
        }
    }

    @Override
    public void run(String... args) throws Exception {
        if (enableDubboTest == null) {
            return;
        }

        try {
            String[] packageNames = getBasePackages();
            verifyPackagesAccessibility(packageNames);
            for (String packageName : packageNames) {
                scanPackage(packageName);
            }
            printScanResults();
        } catch (Exception e) {
            log.error("Dubbo接口扫描失败: " + e.getMessage(), e);
        }
    }

    private void verifyPackagesAccessibility(String[] packageNames) {
        for (String packageName : packageNames) {
            try {
                ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                String searchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                        ClassUtils.convertClassNameToResourcePath(packageName) + "/" + RESOURCE_PATTERN;

                Resource[] resources = resolver.getResources(searchPath);
                if (packageName.contains("common")) {
                    int commonClassCount = 0;
                    for (Resource resource : resources) {
                        if (resource.getFilename() != null && resource.getFilename().endsWith(".class")) {
                            commonClassCount++;
                        }
                    }
                }

            } catch (Exception e) {
            }
        }
    }

    /**
     * 获取主类名 - 多种方法确保可靠性
     */
    private String getMainClassName() {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if ("main".equals(element.getMethodName())) {
                    String mainClassName = element.getClassName();
                    return mainClassName;
                }
            }
        } catch (Exception e) {
            log.warn("通过堆栈跟踪获取主类失败: " + e.getMessage());
        }

        try {
            String mainClassFromProperty = System.getProperty("sun.java.command");
            if (mainClassFromProperty != null && !mainClassFromProperty.trim().isEmpty()) {
                String[] parts = mainClassFromProperty.split("\\s+");
                if (parts.length > 0) {
                    String mainClassName = parts[0];
                    if (mainClassName.endsWith(".jar")) {
                    } else {
                        return mainClassName;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("通过系统属性获取主类失败: " + e.getMessage());
        }
        throw new RuntimeException("无法找到主类名");
    }

    private boolean isAnnotationPresentOnMainClass(Class<?> annotationClass) {
        try {

            String mainClassName = getMainClassName();
            Class<?> mainClass = Class.forName(mainClassName);
            Annotation[] annotations = mainClass.getAnnotations();
            for (Annotation annotation : annotations) {
                log.info("  - 注解: {}", annotation.annotationType().getSimpleName());
            }
            boolean hasAnnotation = mainClass.isAnnotationPresent((Class<? extends Annotation>) annotationClass);
            log.info("@EnableDubboTest 注解检查结果: {}", hasAnnotation);

            if (hasAnnotation) {
                EnableDubboTest enableDubboTest = mainClass.getAnnotation(EnableDubboTest.class);
                log.info("✅ 找到 @EnableDubboTest 注解: {}", enableDubboTest);
            } else {
                log.warn("❌ 启动类 {} 上没有找到 @EnableDubboTest 注解", mainClass.getName());
            }

            return hasAnnotation;

        } catch (Exception e) {
            log.error("检查注解时发生异常: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取基础包名
     * 优先使用@EnableDubboTest注解配置的包
     */
    private String[] getBasePackages() {
        if (enableDubboTest != null && enableDubboTest.basePackages().length > 0) {
            log.info("使用注解配置的扫描包: {}", Arrays.toString(enableDubboTest.basePackages()));
            return enableDubboTest.basePackages();
        }
        try {
            String mainClassName = getMainClassName();
            Class<?> mainClass = Class.forName(mainClassName);
            String basePackage = mainClass.getPackage().getName();
            log.info("使用主类包作为扫描包: {}", basePackage);
            return new String[]{basePackage};
        } catch (Exception e) {
            log.warn("获取主类包失败，使用默认包: {}", e.getMessage());
        }
        log.info("使用默认扫描包: com.dubbo.dlt");
        return new String[]{"com.dubbo.dlt"};
    }

    /**
     * 扫描指定包
     */
    private void scanPackage(String basePackage) throws Exception {
        log.info("开始扫描包: {}", basePackage);

        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(basePackage) + "/" + RESOURCE_PATTERN;

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);

        Resource[] resources = resolver.getResources(packageSearchPath);
        log.info("在包 {} 中找到 {} 个资源", basePackage, resources.length);

        int interfaceCount = 0;
        int methodCount = 0;
        int classCount = 0;

        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }

            try {
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                String className = metadataReader.getClassMetadata().getClassName();
                classCount++;

                // 检查是否是接口
                if (metadataReader.getClassMetadata().isInterface()) {
                    Class<?> clazz = Class.forName(className);
                    int methodsInInterface = scanInterfaceMethods(clazz);
                    if (methodsInInterface > 0) {
                        interfaceCount++;
                        methodCount += methodsInInterface;
                    }
                }
            } catch (Exception e) {
                log.warn("扫描类失败: {} - {}", resource.getFilename(), e.getMessage());
            }
        }

        log.info("包 {} 扫描完成: 找到 {} 个类，{} 个接口，{} 个方法",
                basePackage, classCount, interfaceCount, methodCount);
    }

    /**
     * 扫描接口的所有方法
     */
    private int scanInterfaceMethods(Class<?> interfaceClass) {
        List<Method> annotatedMethods = new ArrayList<>();

        for (Method method : interfaceClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(DubboInvokeStat.class)) {
                DubboInvokeStat annotation = method.getAnnotation(DubboInvokeStat.class);
                if (annotation != null) {
                    annotatedMethods.add(method);
                }
            }
        }

        if (!annotatedMethods.isEmpty()) {
            interfaceMethods.put(interfaceClass, annotatedMethods);
            log.info("接口 {} 中找到 {} 个带 @DubboInvokeStat 注解的方法",
                    interfaceClass.getName(), annotatedMethods.size());

            if (interfaceClass.getName().contains("dubbo.common")) {
                log.info("✅ 发现来自 dubbo-common 的接口: {}", interfaceClass.getName());
            }
        }

        return annotatedMethods.size();
    }

    /**
     * 打印扫描结果
     */
    private void printScanResults() {
        if (interfaceMethods.isEmpty()) {
            log.warn("❌ 未发现任何带有@DubboInvokeStat注解的接口");
            System.out.println("未发现任何带有@DubboInvokeStat注解的接口");
            return;
        }

        String result = String.format("✅ 发现 %d 个接口，包含 %d 个测试方法:",
                interfaceMethods.size(), getAllMethods().size());
        log.info(result);
        System.out.println(result);

        long commonInterfaceCount = interfaceMethods.keySet().stream()
                .filter(cls -> cls.getName().contains("dubbo.common"))
                .count();
        long consumerInterfaceCount = interfaceMethods.size() - commonInterfaceCount;

        log.info("接口来源统计: dubbo-common={}, dubbo-consumer={}",
                commonInterfaceCount, consumerInterfaceCount);

        interfaceMethods.forEach((interfaceClass, methods) -> {
            String interfaceResult = String.format("接口: %s (%d 个方法)",
                    interfaceClass.getName(), methods.size());
            log.info(interfaceResult);
            System.out.println(interfaceResult);

            if (interfaceClass.getName().contains("dubbo.common")) {
                System.out.println("  📦 来源: dubbo-common");
            } else if (interfaceClass.getName().contains("dubbo.consumer")) {
                System.out.println("  📦 来源: dubbo-consumer");
            }

            for (Method method : methods) {
                DubboInvokeStat annotation = method.getAnnotation(DubboInvokeStat.class);
                String methodResult = String.format("  ├─ 方法: %s.%s - %s",
                        interfaceClass.getSimpleName(), method.getName(), annotation.value());
                log.info(methodResult);
                System.out.println(methodResult);
            }
        });
    }

    /**
     * 获取所有扫描到的接口
     */
    public Set<Class<?>> getAllInterfaces() {
        return interfaceMethods.keySet();
    }

    /**
     * 获取接口的所有测试方法
     */
    public List<Method> getInterfaceMethods(Class<?> interfaceClass) {
        return interfaceMethods.getOrDefault(interfaceClass, Collections.emptyList());
    }

    /**
     * 获取所有测试方法
     */
    public List<Method> getAllMethods() {
        List<Method> allMethods = new ArrayList<>();
        interfaceMethods.values().forEach(allMethods::addAll);
        return allMethods;
    }

    /**
     * 根据注解value值查找所有方法（扁平化，不按接口分组）
     */
    public List<Method> findAllMethodsByAnnotationValue(String annotationValue) {
        List<Method> result = new ArrayList<>();

        if (annotationValue == null || annotationValue.trim().isEmpty()) {
            return result;
        }

        annotationValue = annotationValue.trim();
        log.info("根据注解值 '{}' 查找方法", annotationValue);

        int foundCount = 0;
        for (List<Method> methods : interfaceMethods.values()) {
            for (Method method : methods) {
                DubboInvokeStat annotation = method.getAnnotation(DubboInvokeStat.class);
                if (annotation != null && annotationValue.equals(annotation.value())) {
                    result.add(method);
                    foundCount++;
                }
            }
        }

        log.info("找到 {} 个匹配的方法", foundCount);
        return result;
    }
}
