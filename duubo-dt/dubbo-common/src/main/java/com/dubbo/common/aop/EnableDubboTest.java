package com.dubbo.common.aop;

import com.dubbo.common.consumer.NettyConsumer;
import com.dubbo.common.netty.lister.MessageListener;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(NettyConsumer.class)
public @interface EnableDubboTest {

    String[] basePackages() default {};

    Class<? extends Annotation>[] annotationTypes() default {DubboInvokeStat.class};

}