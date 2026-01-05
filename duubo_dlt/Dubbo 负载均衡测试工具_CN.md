# Dubbo 负载均衡测试工具

https://img.shields.io/badge/build-passing-brightgreen

本项目专注于测试 Dubbo 框架在不同负载均衡算法下的性能表现。通过 Docker 容器化技术，帮助开发者快速测试项目中负载均衡算法的实际使用情况。

## 如何运行测试

克隆本项目到本地，然后

### Consumer 端配置

- 首先在需要测试的 Consumer 上添加注解：

```java
@EnableDubboTest
```

- 在抽象 API 上添加注解：

```java
@DubboInvokeStat(value = "测试的agent名称", argValue = 枚举值)
```

- 在 Consumer 的启动类里加入 NettyConsumer 的 Bean：

```java
@Bean
public NettyConsumer nettyConsumer()
或者手动注入这个Bean
```

- 将 mock 数据添加到DubboInvokeEnum枚举的中，最后启动 Consumer

### Provider 端配置

- 直接在启动类里加入 Netty 的 Producer：

java

```
@Bean
public NettyProducer nettyProducer()
或者手动注入Bean
```

### 使用 Docker 启动测试环境

- 首先给脚本添加执行权限：

```bash
chmod +x dubbo_dlt.sh
```

- 启动脚本并输入参数：

bash

```
./dubbo_dlt.sh
```

- 最后使用 Docker Compose 启动：

bash

```
docker compose up -d
```