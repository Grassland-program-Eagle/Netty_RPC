package com.mszlu.rpc.annontation;

import java.lang.annotation.*;
//TYPE代表可以放在 类和接口上面
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MsHttpClient {
    //必填 代表bean的名称
    String value();
}
