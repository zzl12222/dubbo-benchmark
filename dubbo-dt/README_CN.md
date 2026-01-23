# Dubbo 测试工具

https://img.shields.io/badge/build-passing-brightgreen

本项目专注于测试 Dubbo 框架的性能表现。通过 Docker 容器化技术，帮助开发者快速测试项目中负载均衡算法的实际使用情况。

## 如何运行测试

### 方式一：docker部署

#### 1.执行命令

bash

```
# 进入项目目录
cd dubbo-dt

# 添加执行权限
chmod +x dubbo-dt.sh

# 运行配置脚本（可以直接回车全部默认）
./dubbo-dt.sh

# 启动所有服务
docker compose up -d
```



#### 2.配置参数（可选）

| 参数               | 描述                      | 默认值                                                       |
| :----------------- | :------------------------ | :----------------------------------------------------------- |
| Dubbo Consumer数量 | 输入Consumer数量          | 1                                                            |
| Agent负载均衡策略  | 输入Agent负载均衡策略     | ConsistentHash                                               |
| Agent测试模式      | 输入Agent的测试方法       | FIXED_COUN(默认固定模式) 比如：SELFFUNCTION(自测模式) DURATION(持续时间模式) |
| Agent测试时长      | 输入Agent测试持续时间(秒) | 100秒(如果需要测试自己的方法，就必须加这个)                  |
| Agent测试请求数    | 输入Agent测试请求总数     | 100次                                                        |
| Agent序列化方式    | 输入Agent序列化方式       | hessian2                                                     |
| Agent命名空间      | agent的命名空间           | dubbo-agent                                                  |
| Dubbo Provider数量 | 输入Provider数量          | 10                                                           |

#### 3.生成结果（可选）

| 文件名称                                      | 描述               |
| :-------------------------------------------- | :----------------- |
| fixed_count-consumer-dubbo-consumerCN/EN.html | consumer结果       |
| consumer_result_sayHello.txt                  | json格式的测试结果 |
| provider_result_provider_1CN/EN.html          | provider的数据     |
| provider_result_provider_1.txt                | json格式的测试结果 |

### 方式二：本地部署

#### 1.配置agent参数

| 参数                    | 含义         | 必填 | 默认值      |
| :---------------------- | :----------- | :--- | :---------- |
| SPRING_APPLICATION_NAME | agent名称    | 是   | 无          |
| SERVICE_PORT            | agent端口    | 是   | 无          |
| AGENT_DURATION_SECONDS  | 测试执行时间 | 否   | 100         |
| AGENT_REQUEST_COUNT     | 测试次数     | 否   | 100         |
| AGENT_LOADBALANCE       | 负载均衡方式 | 否   | random      |
| AGENT_SERIALIZATION     | 序列化方式   | 否   | hessian2    |
| AGENT_TEST_MODE         | 测试方式     | 否   | FIXED_COUNT |

启动示例：

```bash
java -DSPRING_APPLICATION_NAME=dubbo-agent -DSERVICE_PORT=8802 -jar
```



#### 2.配置测试方法

**Consumer端配置**

在Consumer上添加注解：

```java
@EnableDubboTest(basePackages = {"com.dubbo.consumer", "com.dubbo.common"}, testModel = "consumer")
```



在抽象API上添加注解：

```java
@DubboInvokeStat(namespace = "agentname", argKey = "AGENT_NAME_HELLO", argValue = DubboInvokeEnum.class)
```



**Mock数据枚举类**

```java
public enum DubboInvokeEnum {
    AGENT_NAME_HELLO(new DubboTest("你好")),
    AGENT_NAME_HELLO2(new DubboTest("你好2")),
    AGENT_OTHER(new DubboTest("扩展测试"));
    
    private Object value;
    
    DubboInvokeEnum(Object reqObj) {
        this.value = reqObj;
    }
    
    public Object getValue() {
        return value;
    }
}
```



**DubboTest测试参数类**

```java
@Data
@AllArgsConstructor
public class DubboTest<T> {
    private T name;
}
```



**Provider端配置**

在Provider上添加注解：

```java
@EnableDubboTest(basePackages = {"com.dubbo.consumer", "com.dubbo.common"}, testModel = "provider")
```