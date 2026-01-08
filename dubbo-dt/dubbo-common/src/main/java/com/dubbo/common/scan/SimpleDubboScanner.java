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
        if (hasAnnotation) {
            String mainClassName = getMainClassName();
            Class<?> mainClass = Class.forName(mainClassName);
            this.enableDubboTest = mainClass.getAnnotation(EnableDubboTest.class);
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
            log.error("Dubbo scan fail: " + e.getMessage(), e);
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
            log.warn("main do fail : " + e.getMessage());
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
            log.warn("not get main param: " + e.getMessage());
        }
        throw new RuntimeException("not find main name");
    }

    private boolean isAnnotationPresentOnMainClass(Class<?> annotationClass) {
        try {

            String mainClassName = getMainClassName();
            Class<?> mainClass = Class.forName(mainClassName);
            Annotation[] annotations = mainClass.getAnnotations();
            for (Annotation annotation : annotations) {
                log.info("  - annotations: {}", annotation.annotationType().getSimpleName());
            }
            boolean hasAnnotation = mainClass.isAnnotationPresent((Class<? extends Annotation>) annotationClass);
            log.info("@EnableDubboTest result: {}", hasAnnotation);

            if (hasAnnotation) {
                EnableDubboTest enableDubboTest = mainClass.getAnnotation(EnableDubboTest.class);
                log.info("✅find  @EnableDubboTest annotation: {}", enableDubboTest);
            } else {
                log.warn("❌ satrtcalsss {} not find  @EnableDubboTest annotation", mainClass.getName());
            }

            return hasAnnotation;

        } catch (Exception e) {
            return false;
        }
    }

    private String[] getBasePackages() {
        if (enableDubboTest != null && enableDubboTest.basePackages().length > 0) {
            return enableDubboTest.basePackages();
        }
        try {
            String mainClassName = getMainClassName();
            Class<?> mainClass = Class.forName(mainClassName);
            String basePackage = mainClass.getPackage().getName();
            return new String[]{basePackage};
        } catch (Exception e) {
        }
        return new String[]{"com.dubbo.dlt"};
    }

    private void scanPackage(String basePackage) throws Exception {

        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(basePackage) + "/" + RESOURCE_PATTERN;

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);

        Resource[] resources = resolver.getResources(packageSearchPath);

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

    }

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

            if (interfaceClass.getName().contains("dubbo.common")) {
                log.info("✅ find . from dubbo-common annotation: {}", interfaceClass.getName());
            }
        }

        return annotatedMethods.size();
    }

    private void printScanResults() {
        if (interfaceMethods.isEmpty()) {
            return;
        }

        long commonInterfaceCount = interfaceMethods.keySet().stream()
                .filter(cls -> cls.getName().contains("dubbo.common"))
                .count();
        long consumerInterfaceCount = interfaceMethods.size() - commonInterfaceCount;
        interfaceMethods.forEach((interfaceClass, methods) -> {
        });
    }

    public Set<Class<?>> getAllInterfaces() {
        return interfaceMethods.keySet();
    }
    public List<Method> getInterfaceMethods(Class<?> interfaceClass) {
        return interfaceMethods.getOrDefault(interfaceClass, Collections.emptyList());
    }

    public List<Method> getAllMethods() {
        List<Method> allMethods = new ArrayList<>();
        interfaceMethods.values().forEach(allMethods::addAll);
        return allMethods;
    }

    public List<Method> findAllMethodsByAnnotationValue(String annotationValue) {
        List<Method> result = new ArrayList<>();

        if (annotationValue == null || annotationValue.trim().isEmpty()) {
            return result;
        }

        annotationValue = annotationValue.trim();

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

        return result;
    }
}
