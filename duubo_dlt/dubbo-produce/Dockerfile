# 多阶段构建
# 阶段1：构建
FROM maven:3.8.4-jdk-8 AS builder

# 设置工作目录
WORKDIR /build

# 复制所有文件
COPY . /build/

# 构建 provider 模块（修复版）
RUN echo "=== 构建 Dubbo Provider ===" && \
    echo "当前目录:" && \
    pwd && ls -la && \
    echo "=== 第一步：安装父POM ===" && \
    mvn clean install -DskipTests -N && \
    echo "=== 第二步：构建common模块 ===" && \
    if [ -d "dubbo-common" ]; then \
        mvn clean install -DskipTests -pl dubbo-common -am; \
    fi && \
    echo "=== 第三步：构建provider模块 ===" && \
    if [ -d "dubbo-provider" ]; then \
        cd dubbo-provider && \
        echo "1. 清理、打包并重新打包（单命令）..." && \
        # 关键修复：合并 package 和 repackage 为一个命令
        mvn clean package spring-boot:repackage -DskipTests && \
        echo "=== 构建完成 ===" && \
        echo "生成的JAR文件:" && \
        find target -name "*.jar" -type f | xargs ls -lh && \
        echo "区分JAR文件类型:" && \
        echo "原始JAR（不可执行）:" && \
        find target -name "original-*.jar" -type f -exec echo "  - {}" \; && \
        echo "可执行JAR（带Main-Class）:" && \
        find target -name "dubbo-provider*.jar" ! -name "original*" -type f -exec echo "  - {}" \; && \
        echo "检查可执行JAR的清单:" && \
        for jar in $(find target -name "dubbo-provider*.jar" ! -name "original*" -type f); do \
            echo "检查 $jar:" && \
            jar tf "$jar" | grep -E "(MANIFEST.MF|Main-Class)" | head -3; \
        done; \
    else \
        echo "错误：dubbo-provider目录不存在" && exit 1; \
    fi

# 阶段2：运行
FROM eclipse-temurin:8-jre

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app
RUN mkdir -p /app/logs && mkdir -p /root/.dubbo

# 复制可执行JAR（排除original-开头的原始JAR）
COPY --from=builder /build/dubbo-provider/target/dubbo-provider-*.jar app.jar

# 简化验证步骤
RUN echo "=== 验证JAR文件 ===" && \
    ls -lh /app/ && \
    echo "检查主类..." && \
    if command -v jar >/dev/null 2>&1; then \
        if jar tf app.jar | grep -q "MANIFEST.MF"; then \
            jar xf app.jar META-INF/MANIFEST.MF 2>/dev/null && \
            if grep -qi "main-class" META-INF/MANIFEST.MF; then \
                echo "✅ JAR包含Main-Class，可以执行"; \
            else \
                echo "❌ JAR缺少Main-Class" && exit 1; \
            fi; \
        else \
            echo "⚠️  无法验证JAR，继续执行"; \
        fi; \
    else \
        echo "⚠️  没有jar命令，跳过验证"; \
    fi

# 暴露端口
EXPOSE 8080 20880

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health 2>/dev/null || curl -f http://localhost:8080/ 2>/dev/null || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]