package com.mszlu.rpc.annontation;

import java.lang.annotation.*;

@Target({ElementType.FIELD,ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MsReference {
//    //netty的服务主机名
//    String host();
//    //netty服务的端口号
//    int port();
    //调用的服务提供方的版本号
    String version() default "1.0";
}
