# 多阶段构建
# 阶段1：构建
FROM maven:3.8.4-jdk-8 AS builder

# 设置工作目录
WORKDIR /build

# 复制所有文件
COPY . /build/

# 设置Maven镜像加速
ENV MAVEN_OPTS="-Dmaven.repo.remote=https://maven.aliyun.com/repository/public -Dmaven.repo.local=/root/.m2/repository"

# 强制重新打包
RUN echo "=== 构建 Dubbo Consumer ===" && \
    echo "=== 第一步：安装父POM ===" && \
    mvn clean install -N -DskipTests && \
    echo "=== 第二步：构建common模块 ===" && \
    cd dubbo-common && \
    mvn clean install -DskipTests && \
    cd .. && \
    echo "=== 第三步：构建consumer模块 ===" && \
    cd dubbo-consumer && \
    mvn clean package spring-boot:repackage -DskipTests && \
    echo "=== 构建完成 ===" && \
    echo "生成的JAR文件:" && \
    ls -lh target/*.jar && \
    echo "检查JAR内容:" && \
    jar tf target/*.jar | grep -E "(DubboInvokeStat|DubboTest)" | head -10

# 阶段2：运行
FROM eclipse-temurin:8-jre

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app
RUN mkdir -p /app/logs

# 复制可执行JAR（排除original-开头的）
COPY --from=builder /build/dubbo-consumer/target/dubbo-consumer-*.jar app.jar

# 检查JAR内容
RUN echo "=== 检查最终JAR内容 ===" && \
    jar tf app.jar | grep -E "(DubboInvokeStat|DubboTest)" | head -10 || echo "未找到相关类"

EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]