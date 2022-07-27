package com.mszlu.rpc.proxy;

import com.mszlu.rpc.annontation.MsMapping;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MsHttpClientProxy implements InvocationHandler {

    public MsHttpClientProxy(){

    }

    //当接口 实现调用的时候，实际上是代理类的invoke方法被调用了
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //实现业务，向服务提供方发起网络请求，获取结果 并返回
        System.out.println("查询商品服务 invoke调用了");
        MsMapping annotation = method.getAnnotation(MsMapping.class);
        if (annotation != null){
            String url = annotation.url();
            ///provide/goods/{id}
            String api = annotation.api();
            Pattern compile = Pattern.compile("(\\{\\w+})");
            Matcher matcher = compile.matcher(api);
            if (matcher.find()){
                int groupCount = matcher.groupCount();
                for (int i=0;i<groupCount;i++){
                    String group = matcher.group(i);
                    api = api.replace(group,args[i].toString());
                }
            }
            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.getForObject(url+api,method.getReturnType());
        }
        return null;
    }

    /**
     * 通过接口 生成代理类
     * @param interfaceClass
     * @param <T>
     * @return
     */
    public <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),new Class<?>[]{interfaceClass},this);
    }
}
