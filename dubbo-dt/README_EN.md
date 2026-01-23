# Dubbo Testing Tool

https://img.shields.io/badge/build-passing-brightgreen

This project focuses on testing the performance of the Dubbo framework. Through Docker containerization technology, it helps developers quickly test the actual usage of load balancing algorithms in their projects.

## How to Run Tests

### Method 1: Docker Deployment

#### 1. Execution Commands

```bash
# Enter the project directory
cd dubbo-dt

# Add execution permissions
chmod +x dubbo-dt.sh

# Run the configuration script (press Enter to use all defaults)
./dubbo-dt.sh

# Start all services
docker compose up -d
```



#### 2. Configuration Parameters (Optional)

| Parameter                     | Description                         | Default Value  |
| :---------------------------- | :---------------------------------- | :------------- |
| Dubbo Consumer Count          | Enter the number of Consumers       | 1              |
| Agent Load Balancing Strategy | Enter Agent load balancing strategy | ConsistentHash |
| Agent Test Mode               | Enter Agent test method             | FIXED_COUNT    |
| Agent Test Duration           | Enter Agent test duration (seconds) | 100 seconds    |
| Agent Test Request Count      | Enter Agent total test requests     | 100 times      |
| Agent Serialization Method    | Enter Agent serialization method    | hessian2       |
| Agent Namespace               | Agent namespace                     | dubbo-agent    |
| Dubbo Provider Count          | Enter Provider count                | 10             |

#### 3. Generated Results (Optional)

| File Name                                     | Description              |
| :-------------------------------------------- | :----------------------- |
| fixed_count-consumer-dubbo-consumerCN/EN.html | Consumer results         |
| consumer_result_sayHello.txt                  | JSON format test results |
| provider_result_provider_1CN/EN.html          | Provider data            |
| provider_result_provider_1.txt                | JSON format test results |

### Method 2: Local Deployment

#### 1. Configure Agent Parameters

| Parameter               | Meaning               | Required | Default Value |
| :---------------------- | :-------------------- | :------- | :------------ |
| SPRING_APPLICATION_NAME | Agent name            | Yes      | None          |
| SERVICE_PORT            | Agent port            | Yes      | None          |
| AGENT_DURATION_SECONDS  | Test execution time   | No       | 100           |
| AGENT_REQUEST_COUNT     | Test count            | No       | 100           |
| AGENT_LOADBALANCE       | Load balancing method | No       | random        |
| AGENT_SERIALIZATION     | Serialization method  | No       | hessian2      |
| AGENT_TEST_MODE         | Test method           | No       | FIXED_COUNT   |

Startup example:

bash

```bash
java -DSPRING_APPLICATION_NAME=dubbo-agent -DSERVICE_PORT=8802 -jar
```



#### 2. Configure Test Methods

**Consumer End Configuration**

Add annotation on Consumer:

java

```java
@EnableDubboTest(basePackages = {"com.dubbo.consumer", "com.dubbo.common"}, testModel = "consumer")
```



Add annotation on abstract API:

java

```java
@DubboInvokeStat(namespace = "agentname", argKey = "AGENT_NAME_HELLO", argValue = DubboInvokeEnum.class)
```



**Mock Data Enumeration Class**

java

```java
public enum DubboInvokeEnum {
    AGENT_NAME_HELLO(new DubboTest("Hello")),
    AGENT_NAME_HELLO2(new DubboTest("Hello2")),
    AGENT_OTHER(new DubboTest("Extended test"));
    
    private Object value;
    
    DubboInvokeEnum(Object reqObj) {
        this.value = reqObj;
    }
    
    public Object getValue() {
        return value;
    }
}
```



**DubboTest Test Parameter Class**

java

```java
@Data
@AllArgsConstructor
public class DubboTest<T> {
    private T name;
}
```



**Provider End Configuration**

Add annotation on Provider:

java

```java
@EnableDubboTest(basePackages = {"com.dubbo.consumer", "com.dubbo.common"}, testModel = "provider")
```