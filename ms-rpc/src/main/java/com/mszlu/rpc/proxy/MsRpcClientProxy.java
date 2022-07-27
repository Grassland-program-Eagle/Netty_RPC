package com.mszlu.rpc.proxy;

import com.mszlu.rpc.annontation.MsReference;
import com.mszlu.rpc.exception.MsRpcException;
import com.mszlu.rpc.factory.SingletonFactory;
import com.mszlu.rpc.message.MsRequest;
import com.mszlu.rpc.message.MsResponse;
import com.mszlu.rpc.netty.client.NettyClient;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class MsRpcClientProxy implements InvocationHandler {

    private MsReference msReference;
    private NettyClient nettyClient;

    public MsRpcClientProxy(MsReference msReference,NettyClient nettyClient) {
        this.msReference = msReference;
        this.nettyClient = nettyClient;
    }

    //当接口 实现调用的时候，实际上是代理类的invoke方法被调用了
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //实现业务，向服务提供方发起网络请求，获取结果 并返回
        log.info("rpc 服务消费方 发起了调用..... invoke调用了");
        //1. 构建请求数据MsRequest
        //2. 创建Netty客户端
        //3. 通过客户端向服务端发送请求
        //4. 接收数据
        String version = msReference.version();
        MsRequest msRequest = MsRequest.builder()
                .group("ms-rpc")
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .version(version)
                .parameters(args)
                .paramTypes(method.getParameterTypes())
                .requestId(UUID.randomUUID().toString())
                .build();
        Object sendRequest = nettyClient.sendRequest(msRequest);
        CompletableFuture<MsResponse<Object>> resultCompletableFuture = (CompletableFuture<MsResponse<Object>>) sendRequest;

        MsResponse<Object> msResponse = resultCompletableFuture.get();
        if (msResponse == null){
            throw new MsRpcException("服务调用失败");
        }
        if (!msRequest.getRequestId().equals(msResponse.getRequestId())){
            throw new MsRpcException("响应结果和请求不一致");
        }
        return msResponse.getData();
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
