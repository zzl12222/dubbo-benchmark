FROM maven:3.8.4-jdk-8 AS builder

WORKDIR /build

COPY . /build/

RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '  <mirrors>' >> /root/.m2/settings.xml && \
    echo '    <mirror>' >> /root/.m2/settings.xml && \
    echo '      <id>aliyun</id>' >> /root/.m2/settings.xml && \
    echo '      <name>Aliyun Maven Mirror</name>' >> /root/.m2/settings.xml && \
    echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '      <mirrorOf>central</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '    </mirror>' >> /root/.m2/settings.xml && \
    echo '  </mirrors>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

RUN mvn clean install -DskipTests -N && \
    if [ -d "dubbo-common" ]; then \
        mvn clean install -DskipTests -pl dubbo-common -am; \
    fi && \
    if [ -d "dubbo-provider" ]; then \
        cd dubbo-provider && \
        mvn clean package spring-boot:repackage -DskipTests; \
    else \
        echo "dubbo-provider directory not found" && exit 1; \
    fi

FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app
RUN mkdir -p /app/logs && mkdir -p /root/.dubbo

COPY --from=builder /build/dubbo-provider/target/dubbo-provider-*.jar app.jar

RUN if jar tf app.jar | grep -q "MANIFEST.MF"; then \
        jar xf app.jar META-INF/MANIFEST.MF 2>/dev/null && \
        if ! grep -qi "main-class" META-INF/MANIFEST.MF; then \
            exit 1; \
        fi; \
    fi

EXPOSE 8080 20880

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health 2>/dev/null || curl -f http://localhost:8080/ 2>/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]