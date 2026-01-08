# Dubbo 负载均衡测试工具

https://img.shields.io/badge/build-passing-brightgreen

本项目专注于测试 Dubbo 框架的性能表现。通过 Docker 容器化技术，帮助开发者快速测试项目中负载均衡算法的实际使用情况。

## 如何运行测试

克隆本项目到本地，然后

### Consumer 端配置

- 首先在需要测试的 Consumer 上添加注解：

```java
@EnableDubboTest
```

- 在抽象 API 上添加注解：

```java
 @DubboInvokeStat(namespace = "agentname",  argKey = "AGENT_NAME_HELLO" ,argValue= DubboInvokeEnum.class) 
```
1.namespace : 这次测试的agent名称

2.argKey : 你枚举对于的key的名称

3.argValue : 对应的测试数据枚举
### Mock数据枚举类
#### 重点：key是要测试的mock数据名称，里面是对应的值
```java
package com.dubbo.common.constant;
import com.alibaba.fastjson2.JSON;


public enum DubboInvokeEnum  {
    AGENT_NAME_HELLO(new DubboTest("你好")),
    AGENT_NAME_HELLO2(new DubboTest("你好号2")),
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
### DubboTest
###### 要求你这个参数要和要调用的方法名称一样
```java
package com.dubbo.common.constant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DubboTest<T> {
    private T name;
}

```
### Provider 端配置

- 不需要配置

### 执行脚本
- 首先给脚本添加执行权限：

```bash
chmod +x dubbo_dlt.sh
```

- 启动脚本并输入参数：

bash

```
./dubbo_dlt.sh
要输入的参数1.输入Produce数量(默认: 10)：1
输入Consumer数量(默认: 1): 2
输入Agent负载均衡策略 默认ConsistentHash：
输入Agent的测试方法(FIXED_COUNT : 固定次数模式/DURATION: 持续时长模式):
输入Agent测试持续时间(秒):
输入Agent测试请求总数 默认100:
输入Agent序列化方式 默认hessian2:
```

- 最后使用 Docker Compose 启动：

bash

```
docker compose up -d
```