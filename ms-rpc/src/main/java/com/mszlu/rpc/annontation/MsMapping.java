package com.mszlu.rpc.annontation;


import java.lang.annotation.*;

//可用于方法上
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface MsMapping {
    //api路径
    String api() default "";
    //调用的主机和端口
    String url() default "";
}
