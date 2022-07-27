package com.mszlu.rpc.annontation;

import com.mszlu.rpc.bean.MsBeanDefinitionRegistry;
import com.mszlu.rpc.spring.MsRpcSpringBeanPostProcessor;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(MsRpcSpringBeanPostProcessor.class)
public @interface EnableRpc {

    //nacos主机名
    String nacosHost() default "localhost";
    //nacos端口号
    int nacosPort() default 8848;

    //nacos组，同一个组内 互通，并且组成集群
    String nacosGroup() default "ms-rpc-group";

    //server服务端口
    int serverPort() default 13567;
}
