# Dubbo Load Balance Testing Tool

https://img.shields.io/badge/build-passing-brightgreen

This project focuses on testing the performance of Dubbo framework under different load balancing algorithms. Using Docker containerization technology, it helps developers quickly test the actual usage of load balancing algorithms in their projects.

## How To Run Test

Clone this project onto your desktop, then

### Consumer Side Configuration

- First add annotation to the Consumer that needs testing:

java

```java
@EnableDubboTest
```



- Add annotation to the abstract API:

java

```java
@DubboInvokeStat(value = "test agent name", argValue = enum value)
```



- Add NettyConsumer Bean to Consumer startup class:

```java
@Bean
public NettyConsumer nettyConsumer()
```



- Add mock data to the enum, then start Consumer

### Provider Side Configuration

- Directly add Netty Producer to startup class:

java

```java
@Bean
public NettyProducer nettyProducer()
```



### Start Test Environment with Docker

- First add execute permission to the script:

```bash
chmod +x dubbo_dlt.sh
```



- Start script with parameters:

```bash
./dubbo_dlt.sh
```



- Finally start with Docker Compose:

```bash
docker compose up -d
```