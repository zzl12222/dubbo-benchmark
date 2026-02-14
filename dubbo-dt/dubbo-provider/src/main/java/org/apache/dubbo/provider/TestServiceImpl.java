package org.apache.dubbo.provider;

import org.apache.dubbo.common.api.TestService;

import org.apache.dubbo.common.api.UserProto;
import org.apache.dubbo.config.annotation.DubboService;


@DubboService
public class TestServiceImpl implements TestService {

    @Override
    public String sayHello(String name) {
        try {
            int processTime = (int) (Math.random() * 50);
            System.out.println("执行了");
            Thread.sleep(processTime);
            if (processTime > 35) {
                throw new InterruptedException();
            }
            return String.format("Hello %s from Provider-", name);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    @Override
    public String sayHello2(String name) {
        return name + "你好好呀";
    }

    @Override
    public String sayHeelow(String name) {
        return "";
    }

    @Override
    public UserProto.UserResponse sayHellos(UserProto.UserRequest request) {
        return UserProto.UserResponse.newBuilder().setGreeting("你好").build();
    }
}