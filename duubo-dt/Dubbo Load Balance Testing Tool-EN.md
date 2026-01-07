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
 @DubboInvokeStat(namespace = "agentname",  argKey = "AGENT_NAME_HELLO" ,argValue= DubboInvokeEnum.class) 
```
1.namespace
The agent name for this test run.

2.argKey
The key corresponding to the enum value used for testing.

3.argValue
The enum class that defines the mock test data.

### Mock Data Enum Class

>⚠️ Important

>1.The enum name (key) represents the mock data identifier to be tested.

>2.The enum value is the actual request object passed to the Dubbo invocation.
```java
package com.dubbo.common.constant;
import com.alibaba.fastjson2.JSON;


public enum DubboInvokeEnum  {
    AGENT_NAME_HELLO(new DubboTest("hellow")),
    AGENT_NAME_HELLO2(new DubboTest("hellow")),
    AGENT_OTHER(new DubboTest("hellow"));
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
###### The value of this parameter must be the same as the name of the method being invoked
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
### Provider Side Configuration

- Directly add Netty Producer to startup class:


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